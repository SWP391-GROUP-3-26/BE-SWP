package com.swp391.beswp.repository;

import com.swp391.beswp.entity.MembershipIncludedSport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface MembershipIncludedSportRepository extends JpaRepository<MembershipIncludedSport, Integer> {

    @Query("""
            SELECT CASE WHEN COUNT(mis) > 0 THEN TRUE ELSE FALSE END
            FROM MembershipIncludedSport mis
            WHERE mis.subscription.user.id = :userId
              AND LOWER(mis.subscription.status) = 'active'
              AND mis.subscription.startDate <= :today
              AND mis.subscription.endDate >= :today
              AND mis.subject.id = :subjectId
            """)
    boolean hasActiveIncludedEntitlement(@Param("userId") Integer userId,
                                         @Param("subjectId") Integer subjectId,
                                         @Param("today") LocalDate today);
}
