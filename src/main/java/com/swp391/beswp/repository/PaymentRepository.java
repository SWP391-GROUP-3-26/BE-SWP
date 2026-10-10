package com.swp391.beswp.repository;

import com.swp391.beswp.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByBooking_Id(Integer bookingId);

    boolean existsByBooking_IdAndStatusIgnoreCase(Integer bookingId, String status);
}
