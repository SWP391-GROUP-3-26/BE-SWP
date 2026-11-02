package com.swp391.beswp.service;

import com.swp391.beswp.dto.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceptionistRegistrationServiceTest {
    @Mock UserRepository userRepository;
    @Mock MembershipPackageRepository packageRepository;
    @Mock MemberPackageSubscriptionRepository packageSubscriptionRepository;
    @Mock MemberSubscriptionRepository memberSubscriptionRepository;
    @Mock ClassRepository classRepository;
    @Mock ClassSessionRepository classSessionRepository;
    @Mock ClassBookingRepository classBookingRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ClassReservationService classReservationService;
    @Mock AuditLogService auditLogService;
    @Mock Clock paymentClock;
    @Mock MemberClassBookingService memberBookings;
    @Mock MemberPackageService memberPackages;
    @Mock MemberPaymentService memberPayments;
    @Mock OnlineSubscriptionPaymentService subscriptionPayments;
    @Mock VnpayService vnpay;
    @Mock SepayService sepay;
    @Mock PackageChoiceGroupRepository choiceGroups;
    @Mock PackageIncludedSportRepository includedSports;
    @Mock MembershipIncludedSportRepository includedSnapshots;
    @InjectMocks ReceptionistRegistrationService service;
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("2", "", List.of(new SimpleGrantedAuthority("ROLE_Receptionist")));
    User member, actor;

    @BeforeEach void setup() {
        MockitoAnnotations.openMocks(this);
        when(paymentClock.getZone()).thenReturn(VnpayService.OFFSET);
        when(paymentClock.instant()).thenReturn(Instant.parse("2026-10-20T03:00:00Z"));
        member = new User(); member.setId(7); member.setFullName("Selected Member"); member.setStatus("Active");
        actor = new User(); actor.setId(2); actor.setFullName("Receptionist"); actor.setStatus("Active");
        when(userRepository.findByIdAndRoleRoleNameIgnoreCase(2, "Receptionist")).thenReturn(Optional.of(actor));
        when(userRepository.findByIdAndRoleRoleNameIgnoreCase(7, "Member")).thenReturn(Optional.of(member));
        when(userRepository.findByIdForUpdate(7)).thenReturn(Optional.of(member));
        when(packageRepository.findById(1)).thenReturn(Optional.of(MembershipPackage.builder().id(1).name("Basic")
                .price(new BigDecimal("100000")).duration(30).status("Active").build()));
        when(packageSubscriptionRepository.save(any())).thenAnswer(i -> { MemberSubscription s = i.getArgument(0); s.setId(10); return s; });
        when(paymentRepository.save(any())).thenAnswer(i -> { Payment p = i.getArgument(0); p.setId(20); return p; });
    }

    @Test void cashPurchaseBelongsToMemberAndRecordsReceptionist() {
        var result = service.registerPackage(auth, 7, new ReceptionistPackageRegistrationRequest(1, "Cash"));
        assertEquals(7, result.memberId()); assertEquals("Paid", result.paymentStatus());
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertSame(member, payment.getValue().getSubscription().getUser());
        assertSame(actor, payment.getValue().getReceptionist());
        assertEquals(0, new BigDecimal("100000").compareTo(payment.getValue().getAmount()));
    }

    @Test void renewalCheckoutRoutesCashAndOnlineThroughRenewalAndRecordsReceptionist() {
        for (String channel : List.of("Cash", "Card", "E_Wallet")) {
            reset(memberPackages);
            var data = com.swp391.beswp.dto.MemberPackageSubscriptionData.builder().subscriptionId(11).paymentId(20)
                    .paymentStatus("Cash".equals(channel) ? "Paid" : "Pending").build();
            var response = new com.swp391.beswp.dto.MemberPackageSubscriptionResponse(true, "Gia hạn", data);
            Payment payment = Payment.builder().id(20).build();
            when(paymentRepository.findById(20)).thenReturn(Optional.of(payment));
            when(memberPackages.renewPackage(eq("7"), any())).thenReturn(response);
            when(memberPackages.registerOnlinePackage(eq("7"), any(), eq("127.0.0.1"), eq(true))).thenReturn(response);
            var request = new ReceptionistPackageRegistrationRequest(1, channel, "AFTER_CURRENT", List.of());
            assertSame(response, service.renewPackage(auth, 7, request, "127.0.0.1"));
            assertSame(actor, payment.getReceptionist());
            ArgumentCaptor<com.swp391.beswp.dto.MemberPackageRegistrationRequest> body = ArgumentCaptor.forClass(com.swp391.beswp.dto.MemberPackageRegistrationRequest.class);
            if ("Cash".equals(channel)) {
                verify(memberPackages).renewPackage(eq("7"), body.capture());
                verify(memberPackages, never()).registerOnlinePackage(anyString(), any(), any(), anyBoolean());
            } else {
                verify(memberPackages).registerOnlinePackage(eq("7"), body.capture(), eq("127.0.0.1"), eq(true));
                verify(memberPackages, never()).renewPackage(anyString(), any());
            }
            assertEquals(channel, body.getValue().getPaymentMethod());
            assertEquals("AFTER_CURRENT", body.getValue().getRenewalStart());
            assertEquals(1, body.getValue().getPackageId());
        }
    }

    @Test void onlinePurchaseCreatesPendingSubscriptionAndNeverCounterPaid() {
        service.registerOnlinePackage(auth, 7, new ReceptionistPackageRegistrationRequest(1, "E_Wallet"), "127.0.0.1");
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        Payment p = payment.getValue();
        assertEquals("Pending", p.getStatus()); assertEquals("Pending", p.getSubscription().getStatus());
        assertSame(member, p.getSubscription().getUser()); assertSame(actor, p.getReceptionist());
        assertNotNull(p.getExpiresAt());
        assertThrows(ResponseStatusException.class, () -> service.registerPackage(auth, 7, new ReceptionistPackageRegistrationRequest(1, "Card")));
    }

    @Test void duplicateActiveOrPendingPackageIsRejectedWithoutNewPayment() {
        when(packageSubscriptionRepository.hasActiveSubscription(eq(7), any())).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.registerPackage(auth, 7, new ReceptionistPackageRegistrationRequest(1, "Cash")));
        when(packageSubscriptionRepository.hasActiveSubscription(eq(7), any())).thenReturn(false);
        when(subscriptionPayments.hasPending(7)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.registerOnlinePackage(auth, 7, new ReceptionistPackageRegistrationRequest(1, "Card"), "127.0.0.1"));
        verify(paymentRepository, never()).save(any());
    }

    @Test void onlineClassReusesMemberReservationThenAddsReceptionist() {
        Payment p = Payment.builder().id(20).status("Pending").build();
        when(memberBookings.registerClass(eq("7"), eq(11), any(), eq("127.0.0.1")))
                .thenReturn(MemberClassBookingResponse.builder().success(true).paymentId(20).paymentStatus("Pending").build());
        when(paymentRepository.findById(20)).thenReturn(Optional.of(p));
        service.registerOnlineClass(auth, 7, new ReceptionistClassRegistrationRequest(11, "DIRECT", "Card"), "127.0.0.1");
        assertSame(actor, p.getReceptionist()); assertEquals("Pending", p.getStatus());
    }

    @Test void wrongRoleAndInactiveMemberCannotPurchase() {
        var wrongRole = new UsernamePasswordAuthenticationToken("7", "", List.of(new SimpleGrantedAuthority("ROLE_Member")));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.registerPackage(wrongRole, 7,
                new ReceptionistPackageRegistrationRequest(1, "Cash"))).getStatusCode().value());
        member.setStatus("Inactive");
        assertThrows(ResponseStatusException.class, () -> service.registerPackage(auth, 7, new ReceptionistPackageRegistrationRequest(1, "Cash")));
        verify(paymentRepository, never()).save(any());
    }

    @Test void classCancellationUsesSelectedMemberOwnershipAndActorAudit() {
        service.cancelClass(auth, 7, 45);
        verify(memberBookings).cancelClassBooking("7", 45, null);
        verify(auditLogService).recordLog(eq(actor), eq("CANCEL_BOOKING"), anyString(), contains("#7"));
    }

    @Test void cancellingPendingPackagePreservesHistoryAndRejectsPaidOrOtherOwner() {
        var sub = MemberSubscription.builder().id(10).user(member).status("Pending").build();
        Payment p = Payment.builder().id(20).subscription(sub).status("Pending").build();
        when(paymentRepository.findOnlineSubscriptionMemberId(20)).thenReturn(Optional.of(7));
        when(subscriptionPayments.lock(20)).thenReturn(p);
        service.cancelPendingPayment(auth, 7, 20);
        assertEquals("Failed", p.getStatus()); assertEquals("Cancelled", sub.getStatus());
        p.setStatus("Paid");
        assertThrows(ResponseStatusException.class, () -> service.cancelPendingPayment(auth, 7, 20));
        assertEquals("Paid", p.getStatus());
        when(paymentRepository.findOnlineSubscriptionMemberId(20)).thenReturn(Optional.of(99));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.cancelPendingPayment(auth, 7, 20)).getStatusCode().value());
        verify(paymentRepository, never()).delete(any());
    }

    void classFixture() {
        ClassEntity course = ClassEntity.builder().id(11).name("Yoga").status("Active").maxCapacity(2)
                .date(LocalDate.now(paymentClock).plusDays(1)).startTime(LocalTime.of(8, 0))
                .price(new BigDecimal("50000")).build();
        ClassSession session = ClassSession.builder().id(31).classEntity(course).status("Scheduled")
                .date(LocalDate.now(paymentClock).plusDays(1)).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).build();
        when(classRepository.findByIdForUpdate(11)).thenReturn(Optional.of(course));
        when(classSessionRepository.findUpcomingScheduledSessions(eq(11), any())).thenReturn(List.of(session));
        when(classBookingRepository.save(any())).thenAnswer(i -> { ClassBooking b = i.getArgument(0); b.setId(45); return b; });
    }

    @Test void cashClassBooksAllRemainingSessionsAndRecordsPaidForSelectedMember() {
        classFixture();
        var result = service.registerClass(auth, 7, new ReceptionistClassRegistrationRequest(11, "DIRECT", "Cash"));
        assertEquals("Paid", result.paymentStatus()); assertEquals(new BigDecimal("50000"), result.paymentAmount());
        assertEquals(7, result.memberId()); assertEquals(1, result.sessionCount());
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertSame(member, payment.getValue().getBooking().getUser()); assertSame(actor, payment.getValue().getReceptionist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-21T01:00:00Z", "2026-10-21T01:00:01Z", "2026-10-22T00:00:00Z"})
    void receptionistCannotRegisterStartedClassWithFutureSessions(String time) {
        classFixture();
        ClassEntity course = classRepository.findByIdForUpdate(11).orElseThrow();
        when(classRepository.findById(11)).thenReturn(Optional.of(course));
        ClassSession later = ClassSession.builder().id(32).classEntity(course).status("Scheduled")
                .date(course.getDate().plusDays(3)).startTime(course.getStartTime()).endTime(LocalTime.of(9, 0)).build();
        when(classSessionRepository.findUpcomingScheduledSessions(eq(11), any())).thenReturn(List.of(later));
        assertTrue(service.getClassRegistrationPreview(auth, 7, 11).canRegister());
        when(paymentClock.instant()).thenReturn(Instant.parse(time));
        var preview = service.getClassRegistrationPreview(auth, 7, 11);
        assertFalse(preview.canRegister());
        assertFalse(preview.classInfo().available());
        assertTrue(preview.message().contains("Lớp đã bắt đầu"));
        for (String method : List.of("DIRECT", "PACKAGE")) {
            var error = assertThrows(ResponseStatusException.class, () -> service.registerClass(auth, 7,
                    new ReceptionistClassRegistrationRequest(11, method, "Cash")));
            assertEquals(400, error.getStatusCode().value());
            assertTrue(error.getReason().contains("Lớp đã bắt đầu"));
        }
        verify(classBookingRepository, never()).save(any()); verify(paymentRepository, never()).save(any());
    }

    @Test void duplicateAndFullClassAreRejectedBeforeCreatingBookings() {
        classFixture();
        when(classBookingRepository.existsActiveBookingByUserIdAndClassId(eq(7), eq(11), any())).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.registerClass(auth, 7, new ReceptionistClassRegistrationRequest(11, "DIRECT", "Cash")));
        when(classBookingRepository.existsActiveBookingByUserIdAndClassId(eq(7), eq(11), any())).thenReturn(false);
        when(classBookingRepository.countReservedMembersBySessionId(eq(31), any())).thenReturn(2L);
        assertThrows(ResponseStatusException.class, () -> service.registerClass(auth, 7, new ReceptionistClassRegistrationRequest(11, "DIRECT", "Cash")));
        verify(classBookingRepository, never()).save(any()); verify(paymentRepository, never()).save(any());
    }

    @Test void packageClassRequiresQuotaAndCoverageThroughLastSession() {
        classFixture();
        var pack = MembershipPackage.builder().includedClasses(0).build();
        var sub = MemberSubscription.builder().id(10).membershipPackage(pack).startDate(LocalDate.now(paymentClock))
                .endDate(LocalDate.now(paymentClock).plusDays(30)).build();
        when(memberSubscriptionRepository.findCurrentActiveSubscription(eq(7), any())).thenReturn(Optional.of(sub));
        assertThrows(ResponseStatusException.class, () -> service.registerClass(auth, 7, new ReceptionistClassRegistrationRequest(11, "PACKAGE", null)));
        pack.setIncludedClasses(10); sub.setEndDate(LocalDate.now(paymentClock));
        assertThrows(ResponseStatusException.class, () -> service.registerClass(auth, 7, new ReceptionistClassRegistrationRequest(11, "PACKAGE", null)));
        sub.setEndDate(LocalDate.now(paymentClock).plusDays(30));
        var result = service.registerClass(auth, 7, new ReceptionistClassRegistrationRequest(11, "PACKAGE", null));
        assertEquals(9, result.remainingPackageClasses()); assertNull(result.paymentId()); assertNull(result.paymentChannel());
        verify(paymentRepository, never()).save(any());
    }
}
