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
public class AuditLogItemResponse {

    private Long id;
    private String logCode;
    private AuditLogPerformerDto performer;
    private String category;
    private String action;
    private String actionColor;
    private String actionType;
    private String description;
    private LocalDateTime createdAt;
    private String formattedTime;
}
