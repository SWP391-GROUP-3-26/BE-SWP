package com.swp391.beswp.repository;

import com.swp391.beswp.entity.MemberSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemberSubscriptionRepository extends JpaRepository<MemberSubscription, Integer> {

    @Query("SELECT s FROM MemberSubscription s " +
           "JOIN FETCH s.membershipPackage p " +
           "WHERE s.user.id = :userId " +
           "AND LOWER(s.status) = 'active' " +
           "AND s.endDate >= :today " +
           "ORDER BY s.endDate DESC")
    List<MemberSubscription> findActiveSubscriptionsByUserId(
            @Param("userId") Integer userId,
            @Param("today") LocalDate today
    );

    default Optional<MemberSubscription> findCurrentActiveSubscription(Integer userId, LocalDate today) {
        List<MemberSubscription> list = findActiveSubscriptionsByUserId(userId, today);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
