package com.swp391.beswp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "Training_Exercise_Result", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"Recipient_ID", "Exercise_ID"}))
@Getter @Setter
public class TrainingExerciseResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Result_ID")
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Recipient_ID", nullable = false)
    private TrainingPlanRecipient recipient;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Exercise_ID", nullable = false)
    private TrainingExercise exercise;
    @Column(name = "Completed", nullable = false)
    private boolean completed;
    @Column(name = "Member_Result", length = 2000)
    private String memberResult;
}
