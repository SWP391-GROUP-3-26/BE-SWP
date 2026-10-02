package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegisterResponse {

    private boolean success;
    private String message;
    private RegisterUserData data;

    public static RegisterResponse success(RegisterUserData data) {
        return new RegisterResponse(true, "Dang ky tai khoan thanh cong", data);
    }

    @Getter
    @AllArgsConstructor
    public static class RegisterUserData {

        private Integer userId;
        private String username;
        private String fullName;
        private String email;
        private String phone;
        private String role;
        private String status;
    }
}
