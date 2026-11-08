package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberRegistrationScheduleTest {
    final ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    final MemberPackageSubscriptionRepository subscriptions = mock(MemberPackageSubscriptionRepository.class);
    final ClassReservationService service = new ClassReservationService(bookings, mock(PaymentRepository.class), subscriptions);
    final LocalDateTime now = LocalDateTime.of(2026, 11, 8, 10, 0);
    final LocalDate day = now.toLocalDate().plusDays(5);

    ClassEntity course(int id, int subject, LocalDate start, LocalDate end) {
        return ClassEntity.builder().id(id).subject(Subject.builder().id(subject).build()).name("Class " + id)
                .status("Active").date(start).endDate(end).build();
    }

    ClassSession session(ClassEntity course, LocalDate date, String start, String end) {
        return ClassSession.builder().classEntity(course).date(date).startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end)).status("Scheduled").build();
    }

    void reserved(ClassSession session) {
        when(bookings.findOtherReservedBookings(eq(7), eq(10), any())).thenReturn(List.of(
                ClassBooking.builder().session(session).status("Booked").build()));
    }

    @Test void sameSubjectIsBlockedEvenWithDifferentDaysAndTimes() {
        var target = course(10, 1, day, day.plusDays(30));
        reserved(session(course(11, 1, day.minusDays(1), day.plusDays(10)), day.plusDays(1), "07:00", "08:00"));
        var error = assertThrows(ResponseStatusException.class, () -> service.validateMemberSchedule(7, target,
                List.of(session(target, day, "18:00", "19:00")), now));
        assertEquals(409, error.getStatusCode().value());
        assertEquals(ApiMessages.text("class.registration.sameSubject", "Class 11"), error.getReason());
    }

    @Test void nextSameSubjectCourseAfterPreviousTermIsAllowed() {
        var target = course(10, 1, day.plusDays(11), day.plusDays(30));
        reserved(session(course(11, 1, day, day.plusDays(10)), day, "07:00", "08:00"));
        assertNull(service.registrationConflict(7, target,
                List.of(session(target, day.plusDays(11), "07:00", "08:00")), now));
    }

    @Test void sameSubjectTermsSharingADayAreBlocked() {
        var target = course(10, 1, day.plusDays(10), day.plusDays(30));
        reserved(session(course(11, 1, day, day.plusDays(10)), day.plusDays(10), "07:00", "08:00"));
        assertNotNull(service.registrationConflict(7, target,
                List.of(session(target, day.plusDays(10), "18:00", "19:00")), now));
    }

    @Test void differentSubjectsAreBlockedForAnyOverlappingSessionIncludingContainment() {
        var target = course(10, 1, day, day.plusDays(10));
        reserved(session(course(11, 2, day, day.plusDays(10)), day.plusDays(7), "18:00", "19:00"));
        for (var times : List.of(List.of("17:30", "18:30"), List.of("18:30", "19:30"),
                List.of("17:00", "20:00"), List.of("18:15", "18:45"), List.of("18:00", "19:00"))) {
            assertNotNull(service.registrationConflict(7, target, List.of(
                    session(target, day, "07:00", "08:00"),
                    session(target, day.plusDays(7), times.get(0), times.get(1))), now));
        }
    }

    @Test void adjacentTimesAndDifferentDatesAreAllowed() {
        var target = course(10, 1, day, day.plusDays(10));
        reserved(session(course(11, 2, day, day.plusDays(10)), day, "18:00", "19:00"));
        for (var times : List.of(List.of("17:00", "18:00"), List.of("19:00", "20:00"))) {
            assertNull(service.registrationConflict(7, target, List.of(session(target, day, times.get(0), times.get(1))), now));
        }
        assertNull(service.registrationConflict(7, target, List.of(session(target, day.plusDays(1), "18:00", "19:00")), now));
    }

    @Test void pastAndOutOfTermSessionsDoNotBlock() {
        var target = course(10, 1, day, day.plusDays(30));
        var other = course(11, 1, now.toLocalDate().minusDays(5), day.plusDays(10));
        reserved(session(other, now.toLocalDate(), "07:00", "08:00"));
        assertNull(service.registrationConflict(7, target, List.of(session(target, day, "18:00", "19:00")), now));
        reserved(session(other, day.plusDays(11), "18:00", "19:00"));
        assertNull(service.registrationConflict(7, target, List.of(session(target, day.plusDays(11), "18:00", "19:00")), now));
    }
    @Test void frozenMembershipBlocksNewSessionsDuringFreezeButNotAfterReturn() {
        var frozen = MemberSubscription.builder().status("Frozen").freezeStartedAt(now).freezeEndsAt(now.plusMonths(1)).build();
        when(subscriptions.findFrozenSubscriptions(7)).thenReturn(List.of(frozen));
        var target = course(10, 1, day, day.plusDays(60));
        assertEquals(ApiMessages.text("class.registration.frozen"), service.registrationConflict(7, target,
                List.of(session(target, day, "18:00", "19:00")), now));
        assertNull(service.registrationConflict(7, target,
                List.of(session(target, day.plusMonths(1), "18:00", "19:00")), now));
    }

}
