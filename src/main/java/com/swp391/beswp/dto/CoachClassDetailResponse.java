package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachClassDetailResponse {

    private boolean success;
    private String message;

    private Integer classId;
    private String classCode;
    private String name;
    private Integer subjectId;
    private String subjectName;
    private String subjectCategory;
    private String subjectDescription;
    private Integer roomId;
    private String roomName;
    private Integer roomMaxCapacity;
    private Integer maxCapacity;
    private Integer enrolledStudentsCount;
    private Integer availableSlots;
    private String schedule;
    private String daysOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate startDate;
    private Integer durationWeeks;
    private LocalDate endDate;
    private BigDecimal price;
    private String status;
    private String statusDisplay;

    private List<CoachClassStudentResponse> students;
}
