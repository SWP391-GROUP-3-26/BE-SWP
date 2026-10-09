package com.swp391.beswp.controller;

import com.swp391.beswp.dto.CreateManagedUserRequest;
import com.swp391.beswp.dto.ResetManagedUserPasswordRequest;
import com.swp391.beswp.dto.UpdateManagedUserRequest;
import com.swp391.beswp.dto.UpdateManagedUserRoleRequest;
import com.swp391.beswp.dto.UpdateManagedUserStatusRequest;
import com.swp391.beswp.dto.UserManagementListResponse;
import com.swp391.beswp.dto.UserManagementOptionsResponse;
import com.swp391.beswp.dto.UserManagementResponse;
import com.swp391.beswp.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/center-manager/users")
@RequiredArgsConstructor
public class UserManagementController {

    private static final String CENTER_MANAGER_AUTHORITY = "ROLE_Center Manager";

    private final UserManagementService userManagementService;

    @GetMapping
    public ResponseEntity<UserManagementListResponse> searchUsers(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        requireCenterManager(authentication);
        return ResponseEntity.ok(userManagementService.searchUsers(search, role, status, page, size));
    }

    @GetMapping("/options")
    public ResponseEntity<UserManagementOptionsResponse> getOptions(Authentication authentication) {
        requireCenterManager(authentication);
        return ResponseEntity.ok(userManagementService.getOptions());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportUsers(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status
    ) {
        requireCenterManager(authentication);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users.csv\"");
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(userManagementService.exportCsv(search, role, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserManagementResponse> getUser(
            Authentication authentication,
            @PathVariable Integer id
    ) {
        requireCenterManager(authentication);
        return ResponseEntity.ok(userManagementService.getUser(id));
    }

    @PostMapping
    public ResponseEntity<UserManagementResponse> createUser(
            Authentication authentication,
            @Valid @RequestBody CreateManagedUserRequest request
    ) {
        requireCenterManager(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserManagementResponse> updateUser(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody UpdateManagedUserRequest request
    ) {
        requireCenterManager(authentication);
        return ResponseEntity.ok(userManagementService.updateUser(id, request));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserManagementResponse> updateRole(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody UpdateManagedUserRoleRequest request
    ) {
        requireCenterManager(authentication);
        return ResponseEntity.ok(userManagementService.updateRole(id, request.getRole()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserManagementResponse> updateStatus(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody UpdateManagedUserStatusRequest request
    ) {
        requireCenterManager(authentication);
        return ResponseEntity.ok(userManagementService.updateStatus(id, request.getStatus()));
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<UserManagementResponse> resetPassword(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody ResetManagedUserPasswordRequest request
    ) {
        requireCenterManager(authentication);
        return ResponseEntity.ok(userManagementService.resetPassword(id, request));
    }

    private void requireCenterManager(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean allowed = authentication.getAuthorities().stream()
                .anyMatch(authority -> CENTER_MANAGER_AUTHORITY.equals(authority.getAuthority()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Center Manager role required");
        }
    }
}
