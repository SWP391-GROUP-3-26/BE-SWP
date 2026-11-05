package com.swp391.beswp.controller;

import com.swp391.beswp.dto.MemberNotificationDtos;
import com.swp391.beswp.service.MemberNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/member/notifications")
@RequiredArgsConstructor
public class MemberNotificationController {

    private final MemberNotificationService service;

    @GetMapping
    public MemberNotificationDtos.Result<MemberNotificationDtos.NotificationPage> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return MemberNotificationDtos.Result.ok(service.list(authentication, page, size));
    }

    @GetMapping("/unread-count")
    public MemberNotificationDtos.Result<MemberNotificationDtos.UnreadSummary> unreadCount(
            Authentication authentication
    ) {
        return MemberNotificationDtos.Result.ok(service.unreadSummary(authentication));
    }

    @PutMapping("/{notificationId}/read")
    public MemberNotificationDtos.Result<MemberNotificationDtos.ReadResult> markRead(
            Authentication authentication,
            @PathVariable Long notificationId
    ) {
        return MemberNotificationDtos.Result.ok(service.markRead(authentication, notificationId));
    }
}
