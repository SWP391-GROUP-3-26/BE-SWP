package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record SendRequest(
            @NotNull(message = "Người nhận là bắt buộc")
            @Positive(message = "Người nhận không hợp lệ")
            Integer receiverId,
            @Size(max = 4000, message = "Tin nhắn không được vượt quá 4000 ký tự")
            String content,
            @Size(max = 20, message = "Loại tin nhắn không hợp lệ")
            String messageType,
            @Size(max = 500, message = "Đường dẫn ảnh không hợp lệ")
            String mediaUrl
    ) {
    }

    public record MessageResponse(
            Long messageId,
            Integer conversationId,
            Integer senderId,
            String senderName,
            String senderAvatarUrl,
            Integer receiverId,
            String receiverName,
            String receiverAvatarUrl,
            String content,
            String messageType,
            String mediaUrl,
            boolean read,
            LocalDateTime createdAt
    ) {
    }

    public record ContactResponse(
            Integer conversationId,
            Integer userId,
            String fullName,
            String avatarUrl,
            String role,
            Integer classId,
            String className,
            String subjectName,
            String lastMessage,
            LocalDateTime lastMessageAt,
            long unreadCount
    ) {
    }

    public record ContactListResponse(
            boolean success,
            String message,
            List<ContactResponse> items
    ) {
    }

    public record MessagePageResponse(
            boolean success,
            String message,
            int page,
            int size,
            long totalElements,
            boolean hasNext,
            List<MessageResponse> items
    ) {
    }

    public record ReadResponse(boolean success, String message, int updatedMessages) {
    }

    public record UploadResponse(boolean success, String message, String url) {
    }
}
