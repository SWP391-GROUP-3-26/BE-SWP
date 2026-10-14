package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailableClassCardResponse {

    private java.time.LocalDate startDate;
    private Integer durationWeeks;
    private java.time.LocalDate endDate;
    private Integer sessionId;
    private java.time.LocalDate sessionDate;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
    private java.time.LocalTime sessionStartTime;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
    private java.time.LocalTime sessionEndTime;

    private Integer classId;
    private String classCode;
    private String name;
    private Integer subjectId;
    private String subjectName;
    private String category;
    private String description;
    private String schedule;
    private String daysOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private String roomName;
    private String coachName;
    private String coachAvatar;
    private Integer maxCapacity;
    private Integer enrolledCount;
    private Integer remainingSlots;
    private String capacityUnit;
    private String remainingSlotsText;
    private BigDecimal price;
    private String priceFormatted;
    private String packageNote;
    private boolean enrolled;
    private boolean canRegister;
}
