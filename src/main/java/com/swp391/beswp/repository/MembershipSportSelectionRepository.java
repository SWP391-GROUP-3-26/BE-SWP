package com.swp391.beswp.repository;

import com.swp391.beswp.entity.MembershipSportSelection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MembershipSportSelectionRepository extends JpaRepository<MembershipSportSelection, Integer> {

    @Query("""
            SELECT mss FROM MembershipSportSelection mss
            JOIN FETCH mss.subject
            WHERE mss.subscription.id = :subscriptionId
            ORDER BY mss.choiceGroupId ASC, mss.id ASC
            """)
    List<MembershipSportSelection> findBySubscriptionId(@Param("subscriptionId") Integer subscriptionId);

    /**
     * Kiểm tra Member có quyền sử dụng Sport này qua Selected Entitlement hay không
     * (từ Membership ACTIVE có thời hạn bao trùm ngày hiện tại).
     */
    @Query("""
            SELECT CASE WHEN COUNT(mss) > 0 THEN TRUE ELSE FALSE END
            FROM MembershipSportSelection mss
            WHERE mss.subscription.user.id = :userId
              AND mss.subscription.status = 'Active'
              AND mss.subscription.startDate <= :today
              AND mss.subscription.endDate >= :today
              AND mss.subject.id = :subjectId
            """)
    boolean hasActiveSelectedEntitlement(
            @Param("userId") Integer userId,
            @Param("subjectId") Integer subjectId,
            @Param("today") java.time.LocalDate today
    );
}
