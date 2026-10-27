package com.swp391.beswp.repository;

import com.swp391.beswp.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = {"sender", "receiver"})
    Page<ChatMessage> findByConversation_IdOrderByCreatedAtDescIdDesc(
            Integer conversationId,
            Pageable pageable
    );

    long countByConversation_IdAndReceiver_IdAndReadFalse(Integer conversationId, Integer receiverId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatMessage m SET m.read = true " +
           "WHERE m.conversation.id = :conversationId " +
           "AND m.receiver.id = :receiverId AND m.read = false")
    int markConversationRead(
            @Param("conversationId") Integer conversationId,
            @Param("receiverId") Integer receiverId
    );
}
