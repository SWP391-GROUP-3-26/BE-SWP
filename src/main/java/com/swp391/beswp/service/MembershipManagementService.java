package com.swp391.beswp.service;

import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.MemberPackageSubscriptionRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MembershipManagementService {
    private final MemberPackageSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final AuditLogService audit;

    @Transactional
    public void terminate(String principal, Integer subscriptionId, String reason) {
        if (!StringUtils.hasText(reason)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do không được để trống");
        User manager = users.findById(parseUserId(principal)).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        MemberSubscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy membership"));
        if (!"Active".equalsIgnoreCase(subscription.getStatus()) && !"Pending".equalsIgnoreCase(subscription.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể ngừng membership đang ACTIVE hoặc PENDING");
        }
        subscription.setStatus("Cancelled");
        subscription.setCancellationReason(reason.trim());
        subscription.setCancelledAt(LocalDateTime.now());
        audit.recordLog(manager, "TERMINATE_MEMBERSHIP", "Member_Subscription",
                "Ngừng cung cấp membership #" + subscription.getId() + ": " + reason.trim());
    }

    private Integer parseUserId(String principal) {
        try { return Integer.valueOf(principal); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    }
}
