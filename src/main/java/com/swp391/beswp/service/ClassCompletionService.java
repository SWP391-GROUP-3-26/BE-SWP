package com.swp391.beswp.service;

import com.swp391.beswp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ClassCompletionService {
    private final ClassRepository classes;
    private final ClassSessionRepository sessions;
    private final ClassBookingRepository bookings;
    private final PaymentRepository payments;
    private final Clock paymentClock;

    @Transactional
    public void completeExpiredClasses() {
        LocalDate today = LocalDate.now(paymentClock);
        for (Integer id : classes.findExpiredClassIds(today)) {
            var c = classes.findByIdForUpdate(id).orElse(null);
            if (c == null || !ClassSchedule.isOpen(c) || !ClassSchedule.expired(c, today)) continue;
            c.setStatus("Completed");
            for (var s : sessions.findByClassEntity_Id(id)) {
                if ("Scheduled".equalsIgnoreCase(s.getStatus())) {
                    s.setStatus(ClassSchedule.withinTerm(c, s.getDate()) ? "Completed" : "Cancelled");
                }
            }
            for (var b : bookings.findBySession_ClassEntity_Id(id)) {
                if ("Booked".equalsIgnoreCase(b.getStatus())) {
                    b.setStatus(ClassSchedule.withinTerm(c, b.getSession().getDate()) ? "Completed" : "Cancelled");
                }
                if ("Pending".equalsIgnoreCase(b.getStatus())) b.setStatus("Cancelled");
            }
            for (var p : payments.findByBooking_Session_ClassEntity_IdAndStatus(id, "Pending")) p.setStatus("Failed");
        }
    }
}
