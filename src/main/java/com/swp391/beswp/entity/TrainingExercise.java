package com.swp391.beswp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "Training_Exercise", schema = "dbo")
@Getter
@Setter
public class TrainingExercise {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Exercise_ID")
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Plan_ID", nullable = false)
    private TrainingPlan plan;
    @Column(name = "Position", nullable = false)
    private int position;
    @Column(name = "Training_Date", nullable = false)
    private LocalDate trainingDate;
    @Column(name = "Name", nullable = false, length = 150)
    private String name;
    @Column(name = "Instructions", length = 2000)
    private String instructions;
    @Column(name = "Target", nullable = false, length = 255)
    private String target;
    @Column(name = "Completed", nullable = false)
    private boolean completed;
    @Column(name = "Member_Result", length = 2000)
    private String memberResult;
}
