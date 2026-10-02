package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {

    private Integer userId;
    private String fullName;
    private String username;
    private String email;
    private String role;
    private String status;
}
