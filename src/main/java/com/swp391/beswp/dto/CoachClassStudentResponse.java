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
public class CoachClassStudentResponse {

    private Integer userId;
    private String fullName;
    private String username;
    private String phone;
    private String email;
    private String gender;
    private String avatarUrl;
    private LocalDateTime bookingDatetime;
    private String status;
    private String statusDisplay;
}
