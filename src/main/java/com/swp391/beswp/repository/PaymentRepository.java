package com.swp391.beswp.repository;

import com.swp391.beswp.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    java.util.List<Payment> findByBooking_Session_ClassEntity_IdAndStatus(Integer classId, String status);

    java.util.List<Payment> findAllByBooking_IdOrderByIdDesc(Integer bookingId);

    @org.springframework.data.jpa.repository.Query("SELECT p.booking.session.classEntity.id FROM Payment p WHERE p.id = :id AND p.expiresAt IS NOT NULL AND p.method = 'Card' AND p.paymentType = 'Class_Booking'")
    Optional<Integer> findVnpayClassId(@org.springframework.data.repository.query.Param("id") Integer id);

    @org.springframework.data.jpa.repository.Query("SELECT p.booking.session.classEntity.id FROM Payment p WHERE p.id = :id AND p.expiresAt IS NOT NULL AND p.method IN ('Card', 'E_Wallet') AND p.paymentType = 'Class_Booking'")
    Optional<Integer> findOnlineClassId(@org.springframework.data.repository.query.Param("id") Integer id);

    @org.springframework.data.jpa.repository.Query("SELECT p.booking.session.classEntity.id FROM Payment p WHERE p.id = :id AND p.expiresAt IS NOT NULL AND p.method = 'E_Wallet' AND p.paymentType = 'Class_Booking'")
    Optional<Integer> findSepayClassId(@org.springframework.data.repository.query.Param("id") Integer id);

    java.util.List<Payment> findAllBySubscription_User_IdAndStatus(Integer memberId, String status);

    @org.springframework.data.jpa.repository.Query("SELECT p.subscription.user.id FROM Payment p WHERE p.id = :id AND p.expiresAt IS NOT NULL AND p.method IN ('Card', 'E_Wallet') AND p.paymentType = 'Subscription'")
    Optional<Integer> findOnlineSubscriptionMemberId(@org.springframework.data.repository.query.Param("id") Integer id);

    boolean existsBySepayTransactionId(Long transactionId);

    boolean existsByBooking_IdAndStatusIgnoreCase(Integer bookingId, String status);
}
