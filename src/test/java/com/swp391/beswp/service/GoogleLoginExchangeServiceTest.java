package com.swp391.beswp.service;

import com.swp391.beswp.dto.LoginResponse;
import com.swp391.beswp.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleLoginExchangeServiceTest {

    @Test
    void consumesCodeOnlyOnce() {
        GoogleLoginExchangeService service = new GoogleLoginExchangeService(60);
        LoginResponse loginResponse = LoginResponse.success(
                "token",
                3600,
                new UserResponse(1, "Google Member", "google.member", "member@gmail.com", "Member", "Active")
        );

        String code = service.issue(loginResponse);

        assertEquals("token", service.consume(code).getData().getAccessToken());
        assertThrows(ResponseStatusException.class, () -> service.consume(code));
    }
}
