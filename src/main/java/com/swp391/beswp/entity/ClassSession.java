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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Class_Session", schema = "dbo")
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Session_ID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Class_ID", nullable = false)
    private ClassEntity classEntity;

    @Column(name = "Date", nullable = false)
    private LocalDate date;

    @Column(name = "Start_Time", nullable = false)
    private LocalTime startTime;

    @Column(name = "End_Time", nullable = false)
    private LocalTime endTime;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "Scheduled";
}
