package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyClassItemResponse {

    private Integer registrationId;
    private Integer sessionCount;
    private java.util.List<MemberClassSessionData> sessions;
    private Integer paymentId;
    private String paymentStatus;
    private java.math.BigDecimal paymentAmount;
    private boolean requiresReview;

    private java.time.LocalDate startDate;
    private Integer durationWeeks;
    private java.time.LocalDate endDate;
    private Integer sessionId;
    private java.time.LocalDate sessionDate;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
    private java.time.LocalTime sessionStartTime;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
    private java.time.LocalTime sessionEndTime;

    private Integer bookingId;
    private Integer classId;
    private String classCode;
    private String className;
    private String roomName;
    private String schedule;
    private String timeSlot;
    private String coachName;
    private String coachAvatar;
    private String status;
    private String rawStatus;
    private String classStatus;
    private String sessionStatus;
    private boolean canCancel;
    private boolean canRebook;
    private LocalDateTime bookingDatetime;
    private String paymentMethod;
}
