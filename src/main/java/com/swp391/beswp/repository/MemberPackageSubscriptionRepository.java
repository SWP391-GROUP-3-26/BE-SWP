package com.swp391.beswp.repository;

import com.swp391.beswp.entity.MemberSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MemberPackageSubscriptionRepository extends JpaRepository<MemberSubscription, Integer> {

    @EntityGraph(attributePaths = "membershipPackage")
    @Query("""
            SELECT s FROM MemberSubscription s
            WHERE s.user.id = :userId
              AND (:status IS NULL OR LOWER(s.status) = LOWER(:status))
            ORDER BY s.id DESC
            """)
    Page<MemberSubscription> findMemberSubscriptions(
            @Param("userId") Integer userId,
            @Param("status") String status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "membershipPackage")
    Optional<MemberSubscription> findByIdAndUser_Id(Integer id, Integer userId);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN TRUE ELSE FALSE END
            FROM MemberSubscription s
            WHERE s.user.id = :userId
              AND LOWER(s.status) = 'active'
              AND s.startDate <= :today
              AND s.endDate >= :today
            """)
    boolean hasActiveSubscription(@Param("userId") Integer userId, @Param("today") LocalDate today);
}
