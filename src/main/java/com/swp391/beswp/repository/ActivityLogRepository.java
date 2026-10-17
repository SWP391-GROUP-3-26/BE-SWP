package com.swp391.beswp.repository;

import com.swp391.beswp.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    @Query(value = "SELECT a FROM ActivityLog a " +
           "LEFT JOIN FETCH a.user u " +
           "LEFT JOIN FETCH u.role r " +
           "WHERE (:targetEntity IS NULL OR " +
           "       (LOWER(:targetEntity) = 'package' AND LOWER(a.targetEntity) IN ('package', 'membership_package', 'membership package', 'subscription')) OR " +
           "       (LOWER(:targetEntity) = 'class' AND LOWER(a.targetEntity) IN ('class', 'class_booking', 'class booking', 'session', 'class_session')) OR " +
           "       (LOWER(:targetEntity) = 'user' AND LOWER(a.targetEntity) IN ('user', 'member', 'coach', 'receptionist', 'admin')) OR " +
           "       (LOWER(:targetEntity) = 'payment' AND LOWER(a.targetEntity) IN ('payment', 'payment_history', 'direct_payment')) OR " +
           "       (LOWER(a.targetEntity) = LOWER(:targetEntity))) " +
           "AND (:actionGroup IS NULL OR " +
           "     (:actionGroup = 'ADD' AND (LOWER(a.actionType) LIKE '%create%' OR LOWER(a.actionType) LIKE '%add%' OR LOWER(a.actionType) LIKE '%book%' OR LOWER(a.actionType) LIKE '%insert%' OR LOWER(a.actionType) LIKE '%register%' OR LOWER(a.actionType) LIKE '%purchase%')) OR " +
           "     (:actionGroup = 'UPDATE' AND (LOWER(a.actionType) LIKE '%update%' OR LOWER(a.actionType) LIKE '%edit%' OR LOWER(a.actionType) LIKE '%reset%' OR LOWER(a.actionType) LIKE '%modify%' OR LOWER(a.actionType) LIKE '%login%' OR LOWER(a.actionType) LIKE '%change%')) OR " +
           "     (:actionGroup = 'DELETE' AND (LOWER(a.actionType) LIKE '%delete%' OR LOWER(a.actionType) LIKE '%cancel%' OR LOWER(a.actionType) LIKE '%remove%' OR LOWER(a.actionType) LIKE '%refund%'))) " +
           "AND (:fromDate IS NULL OR a.createdAt >= :fromDate) " +
           "AND (:toDate IS NULL OR a.createdAt <= :toDate) " +
           "AND (:keyword IS NULL OR " +
           "     (:parsedLogId IS NOT NULL AND a.id = :parsedLogId) OR " +
           "     (:parsedUserId IS NOT NULL AND u.id = :parsedUserId) OR " +
           "     LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(a.actionType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(a.targetEntity) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(a) FROM ActivityLog a " +
           "LEFT JOIN a.user u " +
           "WHERE (:targetEntity IS NULL OR " +
           "       (LOWER(:targetEntity) = 'package' AND LOWER(a.targetEntity) IN ('package', 'membership_package', 'membership package', 'subscription')) OR " +
           "       (LOWER(:targetEntity) = 'class' AND LOWER(a.targetEntity) IN ('class', 'class_booking', 'class booking', 'session', 'class_session')) OR " +
           "       (LOWER(:targetEntity) = 'user' AND LOWER(a.targetEntity) IN ('user', 'member', 'coach', 'receptionist', 'admin')) OR " +
           "       (LOWER(:targetEntity) = 'payment' AND LOWER(a.targetEntity) IN ('payment', 'payment_history', 'direct_payment')) OR " +
           "       (LOWER(a.targetEntity) = LOWER(:targetEntity))) " +
           "AND (:actionGroup IS NULL OR " +
           "     (:actionGroup = 'ADD' AND (LOWER(a.actionType) LIKE '%create%' OR LOWER(a.actionType) LIKE '%add%' OR LOWER(a.actionType) LIKE '%book%' OR LOWER(a.actionType) LIKE '%insert%' OR LOWER(a.actionType) LIKE '%register%' OR LOWER(a.actionType) LIKE '%purchase%')) OR " +
           "     (:actionGroup = 'UPDATE' AND (LOWER(a.actionType) LIKE '%update%' OR LOWER(a.actionType) LIKE '%edit%' OR LOWER(a.actionType) LIKE '%reset%' OR LOWER(a.actionType) LIKE '%modify%' OR LOWER(a.actionType) LIKE '%login%' OR LOWER(a.actionType) LIKE '%change%')) OR " +
           "     (:actionGroup = 'DELETE' AND (LOWER(a.actionType) LIKE '%delete%' OR LOWER(a.actionType) LIKE '%cancel%' OR LOWER(a.actionType) LIKE '%remove%' OR LOWER(a.actionType) LIKE '%refund%'))) " +
           "AND (:fromDate IS NULL OR a.createdAt >= :fromDate) " +
           "AND (:toDate IS NULL OR a.createdAt <= :toDate) " +
           "AND (:keyword IS NULL OR " +
           "     (:parsedLogId IS NOT NULL AND a.id = :parsedLogId) OR " +
           "     (:parsedUserId IS NOT NULL AND u.id = :parsedUserId) OR " +
           "     LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(a.actionType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(a.targetEntity) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ActivityLog> searchAuditLogs(
            @Param("targetEntity") String targetEntity,
            @Param("isPackageTarget") boolean isPackageTarget,
            @Param("actionGroup") String actionGroup,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("keyword") String keyword,
            @Param("parsedLogId") Long parsedLogId,
            @Param("parsedUserId") Integer parsedUserId,
            Pageable pageable
    );

    @Query("SELECT DISTINCT a.targetEntity FROM ActivityLog a WHERE a.targetEntity IS NOT NULL AND a.targetEntity <> '' ORDER BY a.targetEntity ASC")
    List<String> findDistinctTargetEntities();
}
