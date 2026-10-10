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
    private boolean canCancel;
    private boolean canRebook;
    private LocalDateTime bookingDatetime;
    private String paymentMethod;
}
