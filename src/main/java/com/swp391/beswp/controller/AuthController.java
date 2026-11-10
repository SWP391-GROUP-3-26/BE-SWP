package com.swp391.beswp.controller;

import com.swp391.beswp.dto.LoginRequest;
import com.swp391.beswp.dto.LoginResponse;
import com.swp391.beswp.dto.RegisterRequest;
import com.swp391.beswp.dto.RegisterResponse;
import com.swp391.beswp.service.AuthService;
import com.swp391.beswp.service.GoogleLoginExchangeService;
import com.swp391.beswp.service.EmailOtpService;
import com.swp391.beswp.dto.EmailOtpDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleLoginExchangeService googleLoginExchangeService;
    private final EmailOtpService emailOtpService;

    @org.springframework.web.bind.annotation.ModelAttribute
    public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<Result<Challenge>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.accepted().body(Result.ok("Đã gửi OTP đăng ký",
                emailOtpService.startRegistration(authService.prepareRegistration(request))));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<RegisterResponse> verifyRegistration(@Valid @RequestBody VerifyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(emailOtpService.verifyRegistration(request));
    }
    @PostMapping("/register/resend")
    public Result<Void> resendRegistration(@Valid @RequestBody ContextRequest request) {
        emailOtpService.resend(request, "REGISTER"); return Result.ok("Đã gửi lại OTP", null);
    }
    @PostMapping("/email-verification/request")
    public Result<Void> requestEmailVerification(@Valid @RequestBody ContextRequest request) {
        emailOtpService.resend(request, "VERIFY_EMAIL"); return Result.ok("Đã gửi OTP xác thực email", null);
    }
    @PostMapping("/email-verification/verify")
    public LoginResponse verifyEmail(@Valid @RequestBody VerifyRequest request) {
        return authService.authenticated(emailOtpService.verifyMember(request));
    }
    @PostMapping("/email-verification/change-email/request")
    public Result<Challenge> requestVerificationEmailChange(@Valid @RequestBody VerificationEmailChangeRequest request) {
        return Result.ok("Đã gửi OTP đến email mới", emailOtpService.requestVerificationEmailChange(request));
    }
    @PostMapping("/email-verification/change-email/resend")
    public Result<Void> resendVerificationEmailChange(@Valid @RequestBody ContextRequest request) {
        emailOtpService.resendEmailChange(request, "VERIFY_CHANGE_EMAIL", null);
        return Result.ok("Đã gửi lại OTP đến email mới", null);
    }
    @PostMapping("/email-verification/change-email/verify")
    public LoginResponse verifyVerificationEmailChange(@Valid @RequestBody VerifyRequest request) {
        return authService.authenticated(emailOtpService.verifyEmailChange(request, "VERIFY_CHANGE_EMAIL", null));
    }
    @PostMapping("/forgot-password")
    public Result<Challenge> forgotPassword(@Valid @RequestBody EmailRequest request) {
        return Result.ok("Đã gửi OTP đặt lại mật khẩu", emailOtpService.requestPasswordReset(request.email()));
    }
    @PostMapping("/forgot-password/resend")
    public Result<Void> resendReset(@Valid @RequestBody ContextRequest request) {
        emailOtpService.resend(request, "RESET"); return Result.ok("Đã gửi lại OTP", null);
    }
    @PostMapping("/forgot-password/verify")
    public Result<ResetGrant> verifyReset(@Valid @RequestBody VerifyRequest request) {
        return Result.ok("Xác thực OTP thành công", emailOtpService.verifyReset(request));
    }
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetRequest request) {
        emailOtpService.resetPassword(request); return Result.ok("Đặt lại mật khẩu thành công, vui lòng đăng nhập lại", null);
    }

    @GetMapping("/google/exchange")
    public ResponseEntity<LoginResponse> exchangeGoogleLoginCode(@RequestParam String code) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(googleLoginExchangeService.consume(code));
    }
}
