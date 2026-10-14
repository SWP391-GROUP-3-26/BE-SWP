package com.swp391.beswp.service;

import com.swp391.beswp.config.ClassCompletionTask;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ClassCompletionServiceTest {
    @Test
    void completionIsIdempotentPreservesCancelledAndHistoryAndDoesNotInventPaid() {
        ClassRepository classes = mock(ClassRepository.class);
        ClassSessionRepository sessions = mock(ClassSessionRepository.class);
        ClassBookingRepository bookings = mock(ClassBookingRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T17:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        ClassEntity c = ClassEntity.builder().id(1).status("Active").date(LocalDate.of(2026, 10, 13))
                .endDate(LocalDate.of(2026, 10, 18)).durationWeeks(1).build();
        ClassSession scheduled = ClassSession.builder().id(1).classEntity(c).date(LocalDate.of(2026, 10, 15)).build();
        ClassSession cancelled = ClassSession.builder().id(2).classEntity(c).status("Cancelled").build();
        ClassBooking booked = ClassBooking.builder().id(1).session(scheduled).status("Booked").build();
        ClassBooking pending = ClassBooking.builder().id(2).session(scheduled).status("Pending").build();
        ClassBooking cancelledBooking = ClassBooking.builder().id(3).session(cancelled).status("Cancelled").build();
        Payment pendingPayment = Payment.builder().booking(pending).status("Pending").build();
        Payment paid = Payment.builder().booking(booked).status("Paid").build();
        when(classes.findExpiredClassIds(LocalDate.of(2026, 10, 19))).thenReturn(List.of(1));
        when(classes.findByIdForUpdate(1)).thenReturn(Optional.of(c));
        when(sessions.findByClassEntity_Id(1)).thenReturn(List.of(scheduled, cancelled));
        when(bookings.findBySession_ClassEntity_Id(1)).thenReturn(List.of(booked, pending, cancelledBooking));
        when(payments.findByBooking_Session_ClassEntity_IdAndStatus(1, "Pending")).thenReturn(List.of(pendingPayment));
        var service = new ClassCompletionService(classes, sessions, bookings, payments, clock);
        service.completeExpiredClasses(); service.completeExpiredClasses();
        assertEquals("Completed", c.getStatus()); assertEquals("Completed", scheduled.getStatus());
        assertEquals("Cancelled", cancelled.getStatus()); assertEquals("Completed", booked.getStatus());
        assertEquals("Cancelled", pending.getStatus()); assertEquals("Cancelled", cancelledBooking.getStatus());
        assertEquals("Failed", pendingPayment.getStatus()); assertEquals("Paid", paid.getStatus());
        verify(sessions, times(1)).findByClassEntity_Id(1);
        verify(classes, never()).delete(any()); verify(bookings, never()).delete(any());
    }

    @Test
    void endDayLegacyAndCancelledClassesAreNeverAutoCompleted() {
        ClassRepository classes = mock(ClassRepository.class);
        ClassSessionRepository sessions = mock(ClassSessionRepository.class);
        ClassBookingRepository bookings = mock(ClassBookingRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T16:59:59Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        var sameDay = ClassEntity.builder().id(1).status("Open").endDate(LocalDate.of(2026, 10, 18)).build();
        var legacy = ClassEntity.builder().id(2).status("Active").date(LocalDate.of(2020, 1, 1)).build();
        var cancelled = ClassEntity.builder().id(3).status("Cancelled").endDate(LocalDate.of(2026, 10, 11)).build();
        when(classes.findExpiredClassIds(any())).thenReturn(List.of(1, 2, 3));
        when(classes.findByIdForUpdate(1)).thenReturn(Optional.of(sameDay));
        when(classes.findByIdForUpdate(2)).thenReturn(Optional.of(legacy));
        when(classes.findByIdForUpdate(3)).thenReturn(Optional.of(cancelled));
        new ClassCompletionService(classes, sessions, bookings, payments, clock).completeExpiredClasses();
        assertEquals("Open", sameDay.getStatus()); assertEquals("Active", legacy.getStatus());
        assertNull(legacy.getDurationWeeks()); assertNull(legacy.getEndDate()); assertEquals("Cancelled", cancelled.getStatus());
        verifyNoInteractions(sessions, bookings, payments);
    }

    @Test
    void taskRunsAtVietnamMidnightAndAlsoOnApplicationReady() throws Exception {
        var method = ClassCompletionTask.class.getMethod("completeExpiredClasses");
        var schedule = method.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertEquals("0 0 0 * * *", schedule.cron()); assertEquals("Asia/Ho_Chi_Minh", schedule.zone());
        assertArrayEquals(new Class<?>[]{org.springframework.boot.context.event.ApplicationReadyEvent.class},
                method.getAnnotation(org.springframework.context.event.EventListener.class).value());
        ClassCompletionService completion = mock(ClassCompletionService.class);
        new ClassCompletionTask(completion).completeExpiredClasses();
        verify(completion).completeExpiredClasses();
    }
}
