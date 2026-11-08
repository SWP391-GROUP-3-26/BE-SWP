package com.swp391.beswp.service;

import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.ClassSession;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ClassReservationService {
    private final ClassBookingRepository bookings;
    private final PaymentRepository payments;

    // Registration callers hold the member write lock until all bookings have been created.
    public void validateMemberSchedule(Integer userId, ClassEntity course, List<ClassSession> sessions, LocalDateTime now) {
        String conflict = registrationConflict(userId, course, sessions, now);
        if (conflict != null) throw new ResponseStatusException(HttpStatus.CONFLICT, conflict);
    }

    public String registrationConflict(Integer userId, ClassEntity course, List<ClassSession> sessions, LocalDateTime now) {
        if (sessions.isEmpty()) return null;
        var start = course.getDate() != null ? course.getDate()
                : sessions.stream().map(ClassSession::getDate).min(java.time.LocalDate::compareTo).orElseThrow();
        var end = course.getEndDate() != null ? course.getEndDate()
                : sessions.stream().map(ClassSession::getDate).max(java.time.LocalDate::compareTo).orElseThrow();
        for (ClassBooking booking : bookings.findOtherReservedBookings(userId, course.getId(), now)) {
            ClassSession existing = booking.getSession();
            ClassEntity other = existing.getClassEntity();
            if (!ClassSchedule.withinTerm(other, existing.getDate())
                    || !existing.getDate().atTime(existing.getEndTime()).isAfter(now)) continue;
            var otherStart = other.getDate() != null ? other.getDate() : existing.getDate();
            var otherEnd = other.getEndDate() != null ? other.getEndDate() : existing.getDate();
            if (course.getSubject() != null && other.getSubject() != null && course.getSubject().getId() != null
                    && Objects.equals(course.getSubject().getId(), other.getSubject().getId())
                    && !start.isAfter(otherEnd) && !end.isBefore(otherStart)) {
                return ApiMessages.text("class.registration.sameSubject", other.getName());
            }
            for (ClassSession selected : sessions) {
                if (selected.getDate().equals(existing.getDate())
                        && selected.getStartTime().isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(selected.getEndTime())) {
                    return ApiMessages.text("class.registration.timeConflict", other.getName(), existing.getDate(),
                            existing.getStartTime(), existing.getEndTime());
                }
            }
        }
        return null;
    }

    public List<ClassBooking> registrationBookings(ClassBooking booking) {
        return booking.getRegistrationCode() == null ? List.of(booking)
                : bookings.findByRegistrationCodeOrderByIdAsc(booking.getRegistrationCode());
    }

    public void cancelPendingRegistration(ClassBooking booking) {
        for (ClassBooking item : registrationBookings(booking)) {
            if ("Pending".equals(item.getStatus())) item.setStatus("Cancelled");
        }
    }

    // Caller holds the class write lock in the same transaction. Queries release expired seats immediately.
    public void expirePending(Integer classId, LocalDateTime now) {
        // Also invalidate old mixed payments before a resume or gateway callback can confirm them.
        for (ClassBooking booking : bookings.findPendingMixedByClassId(classId)) {
            for (ClassBooking item : registrationBookings(booking)) {
                failPendingPayments(item.getId());
            }
            cancelPendingRegistration(booking);
        }
        for (ClassBooking booking : bookings.findExpiredByClassId(classId, now)) {
            cancelPendingRegistration(booking);
            failPendingPayments(booking.getId());
        }
    }

    public void failPendingPayments(Integer bookingId) {
        for (Payment payment : payments.findAllByBooking_IdOrderByIdDesc(bookingId)) {
            if ("Pending".equals(payment.getStatus())) payment.setStatus("Failed");
        }
    }
}
