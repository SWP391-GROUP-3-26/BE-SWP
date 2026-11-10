package com.swp391.beswp.controller;

import com.swp391.beswp.dto.EmailOtpDtos.*;
import com.swp391.beswp.dto.LoginResponse;
import com.swp391.beswp.service.AuthService;
import com.swp391.beswp.service.EmailOtpService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users/me/email-change")
@RequiredArgsConstructor
public class UserEmailChangeController {
    private final EmailOtpService otp;
    private final AuthService auth;

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @PostMapping("/request")
    public Result<Challenge> request(@Valid @RequestBody ProfileEmailChangeRequest request, Authentication authentication) {
        return Result.ok("Đã gửi OTP đến email mới", otp.requestProfileEmailChange(userId(authentication), request));
    }
    @PostMapping("/resend")
    public Result<Void> resend(@Valid @RequestBody ContextRequest request, Authentication authentication) {
        otp.resendEmailChange(request, "CHANGE_EMAIL", userId(authentication));
        return Result.ok("Đã gửi lại OTP đến email mới", null);
    }
    @PostMapping("/verify")
    public LoginResponse verify(@Valid @RequestBody VerifyRequest request, Authentication authentication) {
        return auth.authenticated(otp.verifyEmailChange(request, "CHANGE_EMAIL", userId(authentication)));
    }
    private Integer userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw unauthorized();
        try { return Integer.valueOf(authentication.getName()); }
        catch (NumberFormatException ex) { throw unauthorized(); }
    }
    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Danh tính đăng nhập không hợp lệ");
    }
}
