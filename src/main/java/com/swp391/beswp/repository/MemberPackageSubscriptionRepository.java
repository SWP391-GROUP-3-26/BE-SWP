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
import java.util.List;
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

    /** Kiểm tra Member đang có Membership ACTIVE. */
    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN TRUE ELSE FALSE END
            FROM MemberSubscription s
            WHERE s.user.id = :userId
              AND LOWER(s.status) = 'active'
              AND s.startDate <= :today
              AND s.endDate >= :today
            """)
    boolean hasActiveSubscription(@Param("userId") Integer userId, @Param("today") LocalDate today);

    @Query("""
            SELECT s FROM MemberSubscription s
            WHERE s.user.id = :userId
              AND LOWER(s.status) = 'active'
              AND s.startDate <= :today
              AND s.endDate >= :today
            ORDER BY s.endDate DESC, s.id DESC
            """)
    List<MemberSubscription> findCurrentActiveSubscriptions(@Param("userId") Integer userId,
                                                             @Param("today") LocalDate today);

    /** Kiểm tra Member đang có Membership PENDING (chưa bắt đầu). */
    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN TRUE ELSE FALSE END
            FROM MemberSubscription s
            WHERE s.user.id = :userId
              AND LOWER(s.status) = 'pending'
              AND s.startDate > :today
            """)
    boolean hasPendingSubscription(@Param("userId") Integer userId, @Param("today") LocalDate today);

    /** Lấy các Membership ACTIVE đã quá end_date (để system tự động EXPIRE). */
    @Query("""
            SELECT s FROM MemberSubscription s
            WHERE LOWER(s.status) = 'active'
              AND s.endDate < :today
            """)
    List<MemberSubscription> findExpiredActiveSubscriptions(@Param("today") LocalDate today);

    /** Lấy các Membership PENDING đã đến start_date (để system chuyển sang ACTIVE). */
    @Query("""
            SELECT s FROM MemberSubscription s
            WHERE LOWER(s.status) = 'pending'
              AND s.startDate <= :today
            """)
    List<MemberSubscription> findDuePendingSubscriptions(@Param("today") LocalDate today);

    boolean existsByMembershipPackage_Id(Integer packageId);
}
