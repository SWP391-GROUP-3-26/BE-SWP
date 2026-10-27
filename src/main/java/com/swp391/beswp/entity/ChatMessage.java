package com.swp391.beswp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "Chat_Message", schema = "dbo")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Message_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Conversation_ID", nullable = false)
    private ChatConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Sender_ID", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Receiver_ID", nullable = false)
    private User receiver;

    @Column(name = "Content", columnDefinition = "nvarchar(max)")
    private String content;

    @Column(name = "Message_Type", nullable = false, length = 20)
    private String messageType;

    @Column(name = "Media_URL", length = 500)
    private String mediaUrl;

    @Column(name = "Is_Read", nullable = false)
    private Boolean read;

    @Column(name = "Created_At", nullable = false)
    private LocalDateTime createdAt;
}
