package com.swp391.beswp.service;

import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassReservationService {
    private final ClassBookingRepository bookings;
    private final PaymentRepository payments;

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
