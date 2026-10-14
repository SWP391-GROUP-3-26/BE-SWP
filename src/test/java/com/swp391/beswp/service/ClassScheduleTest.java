package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassScheduleTest {
    @ParameterizedTest
    @CsvSource({"2026-10-13,1,2026-10-18", "2026-10-18,1,2026-10-18", "2026-10-18,2,2026-10-25",
            "2026-10-30,2,2026-11-08", "2026-12-31,2,2027-01-10", "2027-01-01,1,2027-01-03"})
    void calendarWeeksEndOnSunday(String start, int weeks, String end) {
        assertEquals(LocalDate.parse(end), ClassSchedule.endDate(LocalDate.parse(start), weeks));
    }

    @Test
    void firstSessionNeedNotBeFirstCheckboxAndNoSessionsFallOutsideTerm() {
        List<String> days = ClassSchedule.normalizeDays(List.of("Thứ 5", "Thứ 3", "T3"));
        assertEquals(List.of("T3", "T5"), days);
        assertEquals(List.of(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15)),
                ClassSchedule.sessionDates(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 18), days));
        assertThrows(ResponseStatusException.class, () -> ClassSchedule.sessionDates(
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 18), days));
        assertEquals(List.of(LocalDate.of(2026, 10, 15)), ClassSchedule.sessionDates(
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 18), days));
        assertEquals(List.of(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 7)),
                ClassSchedule.sessionDates(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 10), List.of("T2", "T5")));
        assertEquals(List.of(LocalDate.of(2026, 10, 18)), ClassSchedule.sessionDates(
                LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18), List.of("T2", "CN")));
    }

    @Test
    void normalizesExistingVietnameseAndCanonicalDayListsAndRejectsUnknowns() {
        assertEquals(List.of("T2", "T4", "CN"), ClassSchedule.normalizeDays("Thứ 2, Thứ Tư, Chủ nhật"));
        assertEquals(List.of("T2", "T4", "T7"), ClassSchedule.normalizeDays("T2 T4 T7"));
        assertEquals(List.of("T2", "T4", "T7"), ClassSchedule.normalizeDays("  T2 T4 T7  "));
        assertEquals(List.of("T3", "T5"), ClassSchedule.normalizeDays("T3, T5"));
        for (String value : List.of("T8", "Thứ 1", "Thứ 9", "T2,", "T2, abc")) {
            assertThrows(ResponseStatusException.class, () -> ClassSchedule.normalizeDays(value));
        }
        assertThrows(ResponseStatusException.class, () -> ClassSchedule.normalizeDays(List.of()));
    }

    @Test
    void missingNonpositiveAndOverflowingDurationAreRejected() {
        for (Integer weeks : Arrays.asList(null, 0, -1, Integer.MAX_VALUE)) {
            assertThrows(ResponseStatusException.class, () -> ClassSchedule.endDate(LocalDate.of(2026, 10, 13), weeks));
        }
        assertThrows(ResponseStatusException.class, () -> ClassSchedule.endDate(null, 1));
    }

    @Test
    void endDateIsInclusiveLegacyIsNotExpiredAndCancelledStatusIsPreserved() {
        ClassEntity c = ClassEntity.builder().date(LocalDate.of(2026, 10, 13)).endDate(LocalDate.of(2026, 10, 18))
                .durationWeeks(1).daysOfWeek("T3, T5").status("Active").build();
        assertFalse(ClassSchedule.expired(c, LocalDate.of(2026, 10, 18)));
        assertEquals("Active", ClassSchedule.effectiveStatus(c, LocalDate.of(2026, 10, 18)));
        assertEquals("Completed", ClassSchedule.effectiveStatus(c, LocalDate.of(2026, 10, 19)));
        c.setStatus("Cancelled");
        assertEquals("Cancelled", ClassSchedule.effectiveStatus(c, LocalDate.of(2026, 10, 19)));
        c.setStatus("Active"); c.setEndDate(null); c.setDurationWeeks(null);
        assertFalse(ClassSchedule.expired(c, LocalDate.of(2040, 1, 1)));
        assertEquals("Active", ClassSchedule.effectiveStatus(c, LocalDate.of(2040, 1, 1)));
    }

    @Test
    void sessionMustBeFutureScheduledOnSelectedDayAndWithinBounds() {
        ClassEntity c = ClassEntity.builder().date(LocalDate.of(2026, 10, 13)).endDate(LocalDate.of(2026, 10, 18))
                .durationWeeks(1).daysOfWeek("T3, T5").status("Active").build();
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 7, 0);
        ClassSession s = ClassSession.builder().classEntity(c).date(LocalDate.of(2026, 10, 15))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).build();
        assertTrue(ClassSchedule.bookable(c, s, now));
        for (LocalDate date : List.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 20))) {
            s.setDate(date); assertFalse(ClassSchedule.bookable(c, s, now));
        }
        s.setDate(LocalDate.of(2026, 10, 15)); s.setStatus("Cancelled");
        assertFalse(ClassSchedule.bookable(c, s, now));
    }
}
