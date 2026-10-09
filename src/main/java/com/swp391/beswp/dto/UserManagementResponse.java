package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserManagementResponse {

    private boolean success;
    private String message;
    private UserManagementUserData data;

    public static UserManagementResponse success(String message, UserManagementUserData data) {
        return new UserManagementResponse(true, message, data);
    }
}
