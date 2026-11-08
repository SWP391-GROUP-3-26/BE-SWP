package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MembershipFreezeServiceTest {
    final MemberPackageSubscriptionRepository subscriptions = mock(MemberPackageSubscriptionRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final AuditLogService audit = mock(AuditLogService.class);
    final EntityManager em = mock(EntityManager.class);
    final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-07-01T08:00:00Z"));
    final Clock clock = mock(Clock.class);
    final MembershipFreezeService service = new MembershipFreezeService(subscriptions, users, bookings, payments, audit, clock, em);
    User member;
    MemberSubscription sub;

    @BeforeEach void setup() {
        when(clock.getZone()).thenReturn(VnpayService.OFFSET); when(clock.instant()).thenAnswer(i -> time.get());
        Role role = new Role(); role.setRoleName("Member");
        member = new User(); member.setId(7); member.setRole(role); member.setStatus("Active");
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(member));
        sub = MemberSubscription.builder().id(10).user(member).status("Active").durationAtPurchase(365)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
                .freezeMonthsAtPurchase(2).build();
        when(subscriptions.findByIdAndUser_Id(10, 7)).thenReturn(Optional.of(sub));
    }

    @Test void freezeStartsNowUsesCalendarMonthsAndChargesFullRequestedBudget() {
        var result = service.freeze("7", 10, 2);
        assertEquals("Frozen", result.status()); assertEquals(0, result.remainingFreezeMonths());
        assertEquals(LocalDateTime.of(2026, 7, 1, 15, 0), result.freezeStartedAt());
        assertEquals(LocalDateTime.of(2026, 9, 1, 15, 0), result.freezeEndsAt());
        assertEquals(LocalDate.of(2026, 12, 31), sub.getEndDate());
        assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
        assertEquals(2, sub.getFreezeMonthsUsed());
    }

    @Test void earlyReturnAddsOnlyCompleteDaysAndDoesNotRestoreBudget() {
        service.freeze("7", 10, 2); time.set(time.get().plusSeconds(10 * 86400L));
        var result = service.resume("7", 10);
        assertEquals(10, result.extendedDays()); assertEquals(LocalDate.of(2027, 1, 10), result.endDate());
        assertEquals("Active", result.status()); assertEquals(0, result.remainingFreezeMonths());
        assertNull(result.freezeStartedAt()); assertNull(result.freezeEndsAt());
        assertThrows(ResponseStatusException.class, () -> service.resume("7", 10));
        assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
    }

    @Test void lessThan24HoursDoesNotAddADayEvenAcrossMidnight() {
        service.freeze("7", 10, 1); time.set(Instant.parse("2026-07-02T02:00:00Z"));
        var result = service.resume("7", 10);
        assertEquals(0, result.extendedDays()); assertEquals(LocalDate.of(2026, 12, 31), result.endDate());
        assertEquals(1, result.remainingFreezeMonths());
        service.freeze("7", 10, 1);
        assertEquals(2, sub.getFreezeMonthsUsed());
    }

    @Test void monthEndClampsAndAutomaticReturnCapsExtensionAtPlannedEnd() {
        time.set(Instant.parse("2026-01-31T08:00:00Z"));
        service.freeze("7", 10, 1);
        assertEquals(LocalDateTime.of(2026, 2, 28, 15, 0), sub.getFreezeEndsAt());
        when(subscriptions.findDueFrozenSubscriptions(any())).thenReturn(List.of(sub));
        time.set(Instant.parse("2026-03-05T08:00:00Z"));
        service.resumeDueFreezes();
        assertEquals(LocalDate.of(2027, 1, 28), sub.getEndDate());
        assertEquals("Active", sub.getStatus()); assertEquals(1, sub.getFreezeMonthsUsed());
    }

    @Test void paidQueuedRenewalMovesByExactlyTheExtension() {
        var queued = MemberSubscription.builder().id(11).user(member).status("Pending")
                .startDate(LocalDate.of(2027, 1, 1)).endDate(LocalDate.of(2027, 1, 30)).build();
        when(subscriptions.findPaidQueuedSubscriptions(7, sub.getEndDate())).thenReturn(List.of(queued));
        service.freeze("7", 10, 1); time.set(time.get().plusSeconds(10 * 86400L + 86399));
        assertEquals(10, service.resume("7", 10).extendedDays());
        assertEquals(LocalDate.of(2027, 1, 11), queued.getStartDate());
        assertEquals(LocalDate.of(2027, 2, 9), queued.getEndDate()); assertEquals("Pending", queued.getStatus());
    }

    @Test void oldSubscriptionAndShortOriginalTermCannotFreezeDespiteCurrentPackageConfiguration() {
        sub.setMembershipPackage(MembershipPackage.builder().duration(365).freezeMonths(2).build());
        sub.setFreezeMonthsAtPurchase(0);
        assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
        sub.setFreezeMonthsAtPurchase(2); sub.setDurationAtPurchase(90);
        assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
        assertEquals(0, sub.getFreezeMonthsUsed()); assertEquals("Active", sub.getStatus());
    }

    @Test void rejectsInvalidDurationAndInactiveOrExpiredSubscriptions() {
        for (Integer months : Arrays.asList(null, 0, -1, 3)) {
            assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, months));
        }
        for (String status : List.of("Pending", "Cancelled", "Expired")) {
            sub.setStatus(status); assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
        }
        sub.setStatus("Active"); sub.setEndDate(LocalDate.of(2026, 6, 30));
        assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
    }

    @Test void ownershipAndMemberStatusAreCheckedBeforeMutation() {
        when(subscriptions.findByIdAndUser_Id(99, 7)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.freeze("7", 99, 1)).getStatusCode().value());
        member.setStatus("Locked");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.resume("7", 10)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.freeze("invalid", 10, 1)).getStatusCode().value());
    }

    @Test void registeredSessionInsideFreezeOrInProgressBlocksBeforeChargingBudget() {
        var course = ClassEntity.builder().id(1).name("Bơi").build();
        var session = ClassSession.builder().classEntity(course).date(LocalDate.of(2026, 7, 1))
                .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(16, 0)).build();
        when(bookings.findReservedBookingsForFreeze(eq(7), any())).thenReturn(List.of(ClassBooking.builder().session(session).build()));
        assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
        assertEquals("Active", sub.getStatus()); assertEquals(0, sub.getFreezeMonthsUsed());
        session.setDate(LocalDate.of(2026, 7, 15));
        assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
    }

    @Test void sessionExactlyAtReturnTimeDoesNotOverlapFreeze() {
        var session = ClassSession.builder().classEntity(new ClassEntity()).date(LocalDate.of(2026, 8, 1))
                .startTime(LocalTime.of(15, 0)).endTime(LocalTime.of(16, 0)).build();
        when(bookings.findReservedBookingsForFreeze(eq(7), any())).thenReturn(List.of(ClassBooking.builder().session(session).build()));
        assertEquals("Frozen", service.freeze("7", 10, 1).status());
    }

    @Test void pendingPackageCheckoutMustBeResolvedBeforeFreezing() {
        var pending = Payment.builder().expiresAt(LocalDateTime.now(clock).plusMinutes(10)).build();
        when(payments.findAllBySubscription_User_IdAndStatus(7, "Pending")).thenReturn(List.of(
                pending));
        assertThrows(ResponseStatusException.class, () -> service.freeze("7", 10, 1));
        assertEquals("Active", sub.getStatus()); assertEquals(0, sub.getFreezeMonthsUsed());
    }

    @Test void scheduledReturnDoesNotReactivateSubscriptionCancelledWhileWaitingForLock() {
        service.freeze("7", 10, 1); time.set(Instant.parse("2026-08-02T08:00:00Z"));
        when(subscriptions.findDueFrozenSubscriptions(any())).thenReturn(List.of(sub));
        when(users.findByIdForUpdate(7)).thenAnswer(i -> { sub.setStatus("Cancelled"); return Optional.of(member); });
        service.resumeDueFreezes();
        assertEquals("Cancelled", sub.getStatus()); assertEquals(LocalDate.of(2026, 12, 31), sub.getEndDate());
    }
}
