package com.swp391.beswp.repository;

import com.swp391.beswp.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p LEFT JOIN p.booking b LEFT JOIN p.subscription s " +
            "WHERE (b.user.id = :memberId OR s.user.id = :memberId) " +
            "AND (:status IS NULL OR p.status = :status) AND (:type IS NULL OR p.paymentType = :type) " +
            "AND (:from IS NULL OR p.paymentDate >= :from) AND (:until IS NULL OR p.paymentDate < :until) " +
            "ORDER BY p.paymentDate DESC, p.id DESC")
    org.springframework.data.domain.Page<Payment> findMemberHistory(
            @org.springframework.data.repository.query.Param("memberId") Integer memberId,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("type") String type,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("until") java.time.LocalDateTime until,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p WHERE p.status = 'Paid' AND p.requiresReview = true " +
            "AND p.booking IS NOT NULL AND NOT EXISTS (SELECT r.id FROM RefundRequest r WHERE r.payment = p) ORDER BY p.id DESC")
    java.util.List<Payment> findRefundCandidates();

    @org.springframework.data.jpa.repository.Query("SELECT p.booking.session.classEntity.id FROM Payment p WHERE p.id = :id")
    Optional<Integer> findRefundClassId(@org.springframework.data.repository.query.Param("id") Integer id);

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
