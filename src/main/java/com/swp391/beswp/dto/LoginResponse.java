package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private boolean success;
    private String message;
    private LoginData data;

    public static LoginResponse success(String accessToken, long expiresIn, UserResponse user) {
        return new LoginResponse(
                true,
                "Dang nhap thanh cong",
                new LoginData(accessToken, "Bearer", expiresIn, user)
        );
    }

    @Getter
    @AllArgsConstructor
    public static class LoginData {

        private String accessToken;
        private String tokenType;
        private long expiresIn;
        private UserResponse user;
    }
}
