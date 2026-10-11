package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberPackageRegistrationRequest {

    @NotNull(message = "packageId không được để trống")
    @Positive(message = "packageId phải lớn hơn 0")
    private Integer packageId;

    @NotBlank(message = "paymentMethod không được để trống")
    @Size(max = 30, message = "paymentMethod không được vượt quá 30 ký tự")
    private String paymentMethod;
}
