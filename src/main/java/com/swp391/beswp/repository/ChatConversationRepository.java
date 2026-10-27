package com.swp391.beswp.repository;

import com.swp391.beswp.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Integer> {

    Optional<ChatConversation> findByMember_IdAndCoach_Id(Integer memberId, Integer coachId);

    @Query("SELECT c FROM ChatConversation c " +
           "JOIN FETCH c.member member " +
           "JOIN FETCH c.coach coach " +
           "LEFT JOIN FETCH c.classEntity classEntity " +
           "LEFT JOIN FETCH classEntity.subject " +
           "WHERE member.id = :userId OR coach.id = :userId")
    List<ChatConversation> findAllForParticipant(@Param("userId") Integer userId);

    @Query("SELECT c FROM ChatConversation c " +
           "JOIN FETCH c.member member " +
           "JOIN FETCH c.coach coach " +
           "LEFT JOIN FETCH c.classEntity classEntity " +
           "LEFT JOIN FETCH classEntity.subject " +
           "WHERE c.id = :conversationId")
    Optional<ChatConversation> findDetailedById(@Param("conversationId") Integer conversationId);
}
