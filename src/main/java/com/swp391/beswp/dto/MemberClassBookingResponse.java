package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberClassBookingResponse {

    private Integer registrationId;
    private Integer sessionCount;
    private java.util.List<MemberClassSessionData> sessions;

    private java.time.LocalDate startDate;
    private Integer durationWeeks;
    private java.time.LocalDate endDate;
    private Integer sessionId;
    private java.time.LocalDate sessionDate;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
    private java.time.LocalTime sessionStartTime;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
    private java.time.LocalTime sessionEndTime;

    private boolean success;
    private String message;
    private Integer bookingId;
    private Integer classId;
    private String classCode;
    private String className;
    private String schedule;
    private String roomName;
    private String coachName;
    private String paymentMethod;
    private Integer remainingPackageSessions;
    private BigDecimal paymentAmount;
    private String bookingStatus;
    private LocalDateTime bookingDatetime;
    private Integer paymentId;
    private String paymentStatus;
    private String paymentUrl;
    private java.time.OffsetDateTime expiresAt;
}
