package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CoachClassServiceTest {
    @Test
    void coachCountsOnlyConfirmedStudentsButAvailableSlotsIncludeReservations() {
        ClassRepository classes = mock(ClassRepository.class);
        ClassBookingRepository bookings = mock(ClassBookingRepository.class);
        UserRepository users = mock(UserRepository.class);
        Role role = new Role(); role.setRoleName("Coach");
        User coach = new User(); coach.setId(3); coach.setRole(role);
        ClassEntity classEntity = ClassEntity.builder().id(10).coach(coach).status("Active").maxCapacity(3).build();
        when(users.findById(3)).thenReturn(Optional.of(coach));
        when(classes.findClassesByCoach(eq(3), any(), any(), any())).thenReturn(List.of(classEntity));
        when(classes.findById(10)).thenReturn(Optional.of(classEntity));
        when(bookings.countActiveMembersByClassId(10)).thenReturn(1L);
        when(bookings.countReservedMembersByClassId(eq(10), any())).thenReturn(2L);
        var service = new CoachClassService(classes, bookings, users, Clock.fixed(Instant.parse("2026-10-13T01:00:00Z"), VnpayService.OFFSET));
        var item = service.getCoachClasses("3", null, null).getClasses().getFirst();
        assertEquals(1, item.getEnrolledStudentsCount()); assertEquals(1, item.getAvailableSlots());
        var detail = service.getCoachClassDetail("3", 10);
        assertTrue(detail.getStudents().isEmpty()); assertEquals(1, detail.getAvailableSlots());
    }

    @Test
    void expiredClassIsCompletedInCoachFilterAndCountsBeforeScheduledUpdate() {
        ClassRepository classes = mock(ClassRepository.class);
        ClassBookingRepository bookings = mock(ClassBookingRepository.class);
        UserRepository users = mock(UserRepository.class);
        Role role = new Role(); role.setRoleName("Coach");
        User coach = new User(); coach.setId(3); coach.setRole(role);
        ClassEntity c = ClassEntity.builder().id(10).coach(coach).status("Active").maxCapacity(3)
                .date(LocalDate.of(2026, 10, 13)).durationWeeks(1).endDate(LocalDate.of(2026, 10, 18)).daysOfWeek("Thứ 3, Thứ 5").build();
        when(users.findById(3)).thenReturn(Optional.of(coach));
        when(classes.findClassesByCoach(eq(3), any(), any(), any())).thenReturn(List.of(c));
        when(classes.findById(10)).thenReturn(Optional.of(c));
        var service = new CoachClassService(classes, bookings, users,
                Clock.fixed(Instant.parse("2026-10-19T01:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        assertTrue(service.getCoachClasses("3", "Active", null).getClasses().isEmpty());
        var result = service.getCoachClasses("3", "Completed", null);
        assertEquals(1, result.getCounts().getCompletedClasses()); assertEquals(0, result.getCounts().getActiveClasses());
        assertEquals(1, result.getClasses().getFirst().getDurationWeeks());
        assertEquals("Completed", result.getClasses().getFirst().getStatus());
        assertEquals("T3, T5", result.getClasses().getFirst().getDaysOfWeek());
        assertEquals("Completed", service.getCoachClassDetail("3", 10).getStatus());
        assertEquals("Active", c.getStatus());
    }
}
