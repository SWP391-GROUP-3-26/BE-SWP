package com.swp391.beswp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Training_Result", schema = "dbo")
@Getter @Setter
public class TrainingResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Result_ID") private Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Session_ID", nullable = false) private ClassSession session;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "User_ID", nullable = false) private User member;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Coach_ID", nullable = false) private User coach;
    @Column(name = "Training_Content", length = 2000) private String trainingContent;
    @Column(name = "Metric", length = 2000) private String result;
    @Column(name = "Progress_Status", length = 30) private String progressStatus;
    @Column(name = "Coach_Feedback", columnDefinition = "nvarchar(max)") private String coachFeedback;
    @Column(name = "Next_Steps", length = 2000) private String nextSteps;
    @Column(name = "Date", nullable = false) private LocalDate date;
    @Column(name = "Updated_At") private LocalDateTime updatedAt;
    @Column(name = "Is_Withdrawn", nullable = false) private boolean withdrawn;
    @Version @Column(name = "Version", nullable = false) private long version;
}
