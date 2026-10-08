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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Class", schema = "dbo")
public class ClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Class_ID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Subject_ID", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Coach_ID", nullable = false)
    private User coach;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Room_ID", nullable = false)
    private Room room;

    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Column(name = "Max_Capacity", nullable = false)
    private Integer maxCapacity;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @Column(name = "Date")
    private LocalDate date;

    @Column(name = "Time")
    private LocalTime time;

    @Column(name = "Price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "Days_Of_Week", length = 100)
    private String daysOfWeek;

    @Column(name = "Start_Time")
    private LocalTime startTime;

    @Column(name = "End_Time")
    private LocalTime endTime;
}
