package com.swp391.beswp.controller;

import com.swp391.beswp.dto.UpdateUserProfileRequest;
import com.swp391.beswp.dto.UserProfileResponse;
import com.swp391.beswp.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(userProfileService.getMyProfile(authentication.getName()));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateUserProfileRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(userProfileService.updateMyProfile(authentication.getName(), request));
    }
}
