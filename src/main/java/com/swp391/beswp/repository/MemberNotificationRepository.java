package com.swp391.beswp.repository;

import com.swp391.beswp.entity.MemberNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberNotificationRepository extends JpaRepository<MemberNotification, Long> {

    Page<MemberNotification> findByMember_IdOrderByCreatedAtDescIdDesc(Integer memberId, Pageable pageable);

    long countByMember_IdAndReadAtIsNull(Integer memberId);

    Optional<MemberNotification> findByIdAndMember_Id(Long id, Integer memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO dbo.Member_Notification
                (Member_ID, Type, Title, Message, Reference_Type, Reference_ID, Source_Key, Read_At, Created_At)
            SELECT :memberId, 'PACKAGE_EXPIRING', :title, :message,
                   'MEMBERSHIP', :subscriptionId, :sourceKey, NULL, :createdAt
            WHERE NOT EXISTS (
                SELECT 1 FROM dbo.Member_Notification WITH (UPDLOCK, HOLDLOCK)
                WHERE Member_ID = :memberId AND Source_Key = :sourceKey
            )
            """, nativeQuery = true)
    int insertPackageExpiryIfAbsent(
            @Param("memberId") Integer memberId,
            @Param("subscriptionId") Integer subscriptionId,
            @Param("title") String title,
            @Param("message") String message,
            @Param("sourceKey") String sourceKey,
            @Param("createdAt") LocalDateTime createdAt
    );
}
