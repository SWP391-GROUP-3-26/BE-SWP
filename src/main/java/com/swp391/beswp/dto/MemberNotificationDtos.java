package com.swp391.beswp.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class MemberNotificationDtos {

    private MemberNotificationDtos() {
    }

    public record NotificationView(
            Long id,
            String type,
            String title,
            String message,
            String referenceType,
            Integer referenceId,
            boolean read,
            LocalDateTime readAt,
            LocalDateTime createdAt
    ) {
    }

    public record NotificationPage(
            List<NotificationView> items,
            int page,
            int size,
            long totalElements,
            boolean hasNext,
            long unreadCount,
            boolean hasUnread
    ) {
    }

    public record UnreadSummary(long unreadCount, boolean hasUnread) {
    }

    public record ReadResult(NotificationView notification, long unreadCount, boolean hasUnread) {
    }

    public record Result<T>(boolean success, String message, T data) {
        public static <T> Result<T> ok(T data) {
            return new Result<>(true, "Thành công", data);
        }
    }
}
