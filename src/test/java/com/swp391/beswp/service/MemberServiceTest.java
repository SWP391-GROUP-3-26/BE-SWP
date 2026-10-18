package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final MemberSubscriptionRepository subscriptions = mock(MemberSubscriptionRepository.class);
    private final ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    // UTC is still October 15; the business date in Vietnam is already October 16.
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T18:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final LocalDate today = LocalDate.of(2026, 10, 16);
    private final MemberService service = new MemberService(users, subscriptions, bookings, clock);

    @Test
    void detailUsesSelectedMemberAndVietnamDateAndKeepsPackagesEndingToday() {
        givenMember();
        var pack = MembershipPackage.builder().id(3).name("Gói tháng").includedClasses(12).build();
        var current = MemberSubscription.builder().id(1).membershipPackage(pack)
                .startDate(today.minusDays(29)).endDate(today).build();
        var future = MemberSubscription.builder().id(2).membershipPackage(pack)
                .startDate(today.plusDays(1)).endDate(today.plusDays(30)).build();
        when(subscriptions.findActiveSubscriptionsByUserId(42, today)).thenReturn(List.of(current, future));

        var data = service.getMemberById(42).getData();

        assertEquals(42, data.getUserId());
        assertEquals(1, data.getActivePackages().size());
        assertEquals(1, data.getActivePackages().getFirst().subscriptionId());
        assertEquals(today, data.getActivePackages().getFirst().endDate());
        assertTrue(data.getActiveClasses().isEmpty());
        verify(subscriptions).findActiveSubscriptionsByUserId(42, today);
        verify(bookings).findMyBookings(42, "Booked", null);
    }

    @Test
    void detailDeduplicatesClassesAndExcludesExpiredCancelledCompletedAndPastSessions() {
        givenMember();
        var current = booking(1, today, today, "Open", "Scheduled");
        var duplicate = booking(1, today, today, "Open", "Scheduled");
        var future = booking(2, today.plusDays(10), today.plusDays(1), "Active", "Scheduled");
        var expired = booking(3, today.minusDays(1), today, "Active", "Scheduled");
        var cancelledClass = booking(4, today.plusDays(10), today, "Cancelled", "Scheduled");
        var completedClass = booking(5, today.plusDays(10), today, "Completed", "Scheduled");
        var cancelledSession = booking(6, today.plusDays(10), today, "Active", "Cancelled");
        var completedSession = booking(7, today.plusDays(10), today, "Active", "Completed");
        var outsideTerm = booking(8, today, today.plusDays(1), "Active", "Scheduled");
        var pastLegacy = booking(9, null, today.minusDays(1), "Active", "Scheduled");
        var endedToday = booking(10, today, today, "Active", "Scheduled");
        endedToday.getSession().setEndTime(LocalTime.of(0, 30));
        var ongoing = booking(11, today, today, "Active", "Scheduled");
        ongoing.getSession().setStartTime(LocalTime.of(0, 30));
        ongoing.getSession().setEndTime(LocalTime.of(1, 30));
        when(bookings.findMyBookings(42, "Booked", null)).thenReturn(List.of(current, duplicate, future, expired,
                cancelledClass, completedClass, cancelledSession, completedSession, outsideTerm, pastLegacy, endedToday, ongoing));

        var classes = service.getMemberById(42).getData().getActiveClasses();

        assertEquals(List.of(1, 2, 11), classes.stream().map(item -> item.classId()).toList());
        assertEquals("Yoga", classes.getFirst().className());
        assertEquals("Coach", classes.getFirst().coachName());
        assertEquals("Room", classes.getFirst().roomName());
        assertEquals(LocalTime.of(7, 0), classes.getFirst().startTime());
    }

    @Test
    void missingMemberDoesNotReadRegistrations() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.getMemberById(99)).getStatusCode().value());
        verifyNoInteractions(subscriptions, bookings);
    }

    private void givenMember() {
        var role = new Role();
        role.setRoleName("Member");
        var member = new User();
        member.setId(42);
        member.setRole(role);
        when(users.findByIdAndRoleRoleNameIgnoreCase(42, "Member")).thenReturn(Optional.of(member));
    }

    private ClassBooking booking(int classId, LocalDate end, LocalDate sessionDate, String classStatus, String sessionStatus) {
        var coach = new User();
        coach.setFullName("Coach");
        var course = ClassEntity.builder().id(classId).name("Yoga").status(classStatus)
                .subject(Subject.builder().name("Yoga").build()).coach(coach)
                .room(Room.builder().name("Room").build()).date(today.minusDays(7)).endDate(end).build();
        var session = ClassSession.builder().classEntity(course).date(sessionDate)
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(8, 0)).status(sessionStatus).build();
        return ClassBooking.builder().session(session).status("Booked").build();
    }
}
