package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReceptionistPackageRegistrationRequest(
        @NotNull(message = "packageId không được để trống")
        @Positive(message = "packageId phải lớn hơn 0")
        Integer packageId,

        @NotBlank(message = "paymentMethod không được để trống")
        String paymentMethod
) {
}
