package com.swp391.beswp.repository;

import com.swp391.beswp.entity.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Integer> {
    List<TrainingPlan> findByClassEntity_Coach_IdOrderByUpdatedAtDesc(Integer coachId);
    boolean existsByClassEntity_IdAndMember_IdAndStatusAndIdNot(Integer classId, Integer memberId, String status, Integer id);
    boolean existsByClassEntity_IdAndScopeAndStatusAndIdNot(Integer classId, String scope, String status, Integer id);

    @Query("SELECT p FROM TrainingPlan p WHERE p.classEntity IS NOT NULL AND p.publishedRevision > 0 " +
            "AND (p.member.id = :memberId OR EXISTS (SELECT r.id FROM TrainingPlanRecipient r WHERE r.plan = p AND r.member.id = :memberId)) " +
            "ORDER BY p.publishedAt DESC")
    List<TrainingPlan> findVisibleToMember(@Param("memberId") Integer memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM TrainingPlan p WHERE p.id = :id")
    Optional<TrainingPlan> findByIdForUpdate(@Param("id") Integer id);
    @Query("SELECT p.classEntity.id FROM TrainingPlan p WHERE p.id = :id")
    Optional<Integer> findClassId(@Param("id") Integer id);
}
