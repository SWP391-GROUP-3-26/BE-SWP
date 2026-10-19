package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReceptionistClassRegistrationRequest(
        @NotNull(message = "classId không được để trống")
        @Positive(message = "classId phải lớn hơn 0")
        Integer classId,

        @NotBlank(message = "paymentMethod không được để trống")
        String paymentMethod,

        String paymentChannel
) {
}
