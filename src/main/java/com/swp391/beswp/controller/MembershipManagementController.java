package com.swp391.beswp.controller;

import com.swp391.beswp.dto.CancelMembershipRequest;
import com.swp391.beswp.service.MembershipManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/center-manager/memberships")
@RequiredArgsConstructor
public class MembershipManagementController {
    private final MembershipManagementService service;

    @PatchMapping("/{subscriptionId}/terminate")
    public ResponseEntity<Map<String, Object>> terminate(Authentication authentication, @PathVariable Integer subscriptionId,
                                                          @Valid @RequestBody CancelMembershipRequest request) {
        service.terminate(authentication.getName(), subscriptionId, request.reason());
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã ngừng cung cấp membership"));
    }
}
