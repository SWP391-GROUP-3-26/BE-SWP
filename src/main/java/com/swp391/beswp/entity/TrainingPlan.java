package com.swp391.beswp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Training_Plan", schema = "dbo")
@Getter
@Setter
public class TrainingPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Plan_ID")
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Class_ID", nullable = false)
    private ClassEntity classEntity;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Member_ID")
    private User member;
    @Column(name = "Plan_Scope", nullable = false, length = 20)
    private String scope = "INDIVIDUAL";
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Coach_ID", nullable = false)
    private User createdBy;
    @Column(name = "Title", nullable = false, length = 150)
    private String title;
    @Column(name = "Goal", nullable = false, length = 2000)
    private String goal;
    @Column(name = "Assessment", length = 2000)
    private String assessment;
    @Column(name = "Start_Date", nullable = false)
    private LocalDate startDate;
    @Column(name = "End_Date", nullable = false)
    private LocalDate endDate;
    @Column(name = "Status", nullable = false, length = 20)
    private String status = "DRAFT";
    @Column(name = "Coach_Feedback", length = 2000)
    private String coachFeedback;
    @Column(name = "Published_Revision", nullable = false)
    private int publishedRevision;
    @Column(name = "Read_Revision", nullable = false)
    private int readRevision;
    @Column(name = "Published_At")
    private LocalDateTime publishedAt;
    @Column(name = "Created_At", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "Updated_At", nullable = false)
    private LocalDateTime updatedAt;
    @Version @Column(name = "Version", nullable = false)
    private long version;
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<TrainingExercise> exercises = new ArrayList<>();
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<TrainingPlanRecipient> recipients = new ArrayList<>();
}
