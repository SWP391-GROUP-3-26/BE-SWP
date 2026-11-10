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

    public static LoginResponse verificationRequired(EmailOtpDtos.Challenge challenge) {
        LoginData data = new LoginData(null, null, 0, null);
        data.emailVerificationRequired = true;
        data.emailVerification = challenge;
        return new LoginResponse(true, "Vui lòng xác thực email trước khi sử dụng tài khoản", data);
    }

    @Getter
    public static class LoginData {

        private String accessToken;
        private String tokenType;
        private long expiresIn;
        private UserResponse user;
        private boolean emailVerificationRequired;
        private EmailOtpDtos.Challenge emailVerification;

        public LoginData(String accessToken, String tokenType, long expiresIn, UserResponse user) {
            this.accessToken = accessToken; this.tokenType = tokenType; this.expiresIn = expiresIn; this.user = user;
        }
    }
}
