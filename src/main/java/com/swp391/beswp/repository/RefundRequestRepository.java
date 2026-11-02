package com.swp391.beswp.repository;

import com.swp391.beswp.entity.RefundRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Integer> {
    boolean existsByPickupCode(String pickupCode);
    Optional<RefundRequest> findByPayment_Id(Integer paymentId);
    List<RefundRequest> findByPayment_IdIn(Collection<Integer> paymentIds);
    List<RefundRequest> findAllByOrderByUpdatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.id = :id")
    Optional<RefundRequest> findByIdForUpdate(@Param("id") Integer id);
    @Query("SELECT r FROM RefundRequest r WHERE r.payment.booking.user.id = :memberId " +
            "AND r.notificationRevision > r.readRevision ORDER BY r.updatedAt DESC")
    List<RefundRequest> findUnread(@Param("memberId") Integer memberId);
}
