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
}
