package com.swp391.beswp.controller;

import com.swp391.beswp.dto.LoginRequest;
import com.swp391.beswp.dto.LoginResponse;
import com.swp391.beswp.dto.RegisterRequest;
import com.swp391.beswp.dto.RegisterResponse;
import com.swp391.beswp.service.AuthService;
import com.swp391.beswp.service.GoogleLoginExchangeService;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/google/exchange")
    public ResponseEntity<LoginResponse> exchangeGoogleLoginCode(@RequestParam String code) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(googleLoginExchangeService.consume(code));
    }
}
