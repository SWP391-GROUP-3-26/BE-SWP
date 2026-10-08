package com.swp391.beswp.controller;

import com.swp391.beswp.service.AvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String avatarUrl = avatarService.uploadAvatar(authentication.getName(), file);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cập nhật ảnh đại diện thành công",
                "data", Map.of("avatarUrl", avatarUrl)
        ));
    }
}
