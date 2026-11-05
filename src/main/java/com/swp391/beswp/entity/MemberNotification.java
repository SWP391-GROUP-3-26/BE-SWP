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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "Member_Notification",
        schema = "dbo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"Member_ID", "Source_Key"})
)
public class MemberNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Notification_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "Member_ID", nullable = false)
    private User member;

    @Column(name = "Type", nullable = false, length = 40)
    private String type;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Column(name = "Message", nullable = false, length = 1000)
    private String message;

    @Column(name = "Reference_Type", nullable = false, length = 40)
    private String referenceType;

    @Column(name = "Reference_ID", nullable = false)
    private Integer referenceId;

    @Column(name = "Source_Key", nullable = false, length = 200)
    private String sourceKey;

    @Column(name = "Read_At")
    private LocalDateTime readAt;

    @Column(name = "Created_At", nullable = false)
    private LocalDateTime createdAt;
}
