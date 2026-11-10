package com.swp391.beswp.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class EmailOtpDtos {
    private EmailOtpDtos() { }
    public record EmailRequest(@NotBlank @Email @Size(max = 100) String email) { }
    // The stored source address may be invalid legacy data; only the new recipient must pass @Email.
    public record VerificationEmailChangeRequest(@NotBlank @Size(max = 100) String email,
            @NotBlank @Size(max = 100) String verificationToken,
            @NotBlank @Email @Size(max = 100) String newEmail) {
        @Override public String toString() { return "VerificationEmailChangeRequest[redacted]"; }
    }
    public record ProfileEmailChangeRequest(@NotBlank @Email @Size(max = 100) String newEmail,
            @NotBlank @Size(max = 72) String currentPassword) {
        @Override public String toString() { return "ProfileEmailChangeRequest[redacted]"; }
    }
    public record ContextRequest(@NotBlank @Email @Size(max = 100) String email,
                                 @NotBlank @Size(max = 100) String verificationToken) {
        @Override public String toString() { return "ContextRequest[redacted]"; }
    }
    public record VerifyRequest(@NotBlank @Email @Size(max = 100) String email,
                                @NotBlank @Size(max = 100) String verificationToken,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {
        @Override public String toString() { return "VerifyRequest[redacted]"; }
    }
    public record ResetRequest(@NotBlank @Email @Size(max = 100) String email,
                               @NotBlank @Size(max = 100) String resetToken,
                               @NotBlank @Size(min = 8, max = 72) String password,
                               @NotBlank @Size(max = 72) String confirmPassword) {
        @Override public String toString() { return "ResetRequest[redacted]"; }
    }
    public record Challenge(String email, String verificationToken, Instant expiresAt,
                            int otpExpiresInSeconds, int resendAfterSeconds) {
        @Override public String toString() { return "Challenge[redacted]"; }
    }
    public record ResetGrant(String resetToken, Instant expiresAt) {
        @Override public String toString() { return "ResetGrant[redacted]"; }
    }
    public record Result<T>(boolean success, String message, T data) {
        public static <T> Result<T> ok(String message, T data) { return new Result<>(true, message, data); }
        @Override public String toString() { return "Result[success=" + success + "]"; }
    }
}
