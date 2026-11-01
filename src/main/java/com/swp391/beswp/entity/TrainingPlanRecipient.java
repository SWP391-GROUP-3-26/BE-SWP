package com.swp391.beswp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Training_Plan_Recipient", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"Plan_ID", "Member_ID"}))
@Getter @Setter
public class TrainingPlanRecipient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Recipient_ID")
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Plan_ID", nullable = false)
    private TrainingPlan plan;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Member_ID", nullable = false)
    private User member;
    @Column(name = "Read_Revision", nullable = false)
    private int readRevision;
    @Column(name = "Coach_Feedback", length = 2000)
    private String coachFeedback;
    @OneToMany(mappedBy = "recipient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrainingExerciseResult> results = new ArrayList<>();
}
