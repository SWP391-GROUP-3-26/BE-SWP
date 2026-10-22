package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelMembershipRequest(
        @NotBlank(message = "Lý do ngừng cung cấp dịch vụ không được để trống") String reason
) {
}
