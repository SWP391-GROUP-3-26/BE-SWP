package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogPerformerDto {

    private Integer userId;
    private String userCode;
    private String fullName;
    private String username;
    private String role;
    private String avatarInitials;
    private String avatarUrl;
    private String displayText;
}
