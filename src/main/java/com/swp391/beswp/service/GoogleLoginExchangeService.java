package com.swp391.beswp.service;

import com.swp391.beswp.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleLoginExchangeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, PendingLogin> pendingLogins = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public GoogleLoginExchangeService(
            @Value("${app.oauth2.google.exchange-code-ttl-seconds:60}") long ttlSeconds
    ) {
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(LoginResponse loginResponse) {
        removeExpired();
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingLogins.put(code, new PendingLogin(loginResponse, Instant.now().plusSeconds(ttlSeconds)));
        return code;
    }

    public LoginResponse consume(String code) {
        PendingLogin pendingLogin = pendingLogins.remove(code);
        if (pendingLogin == null || !Instant.now().isBefore(pendingLogin.expiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google login code is invalid or expired");
        }
        return pendingLogin.response();
    }

    private void removeExpired() {
        Instant now = Instant.now();
        pendingLogins.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record PendingLogin(LoginResponse response, Instant expiresAt) {
    }
}
