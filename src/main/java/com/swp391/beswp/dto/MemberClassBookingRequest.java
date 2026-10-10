package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberClassBookingRequest {

    private Integer classId;

    /**
     * Hình thức thanh toán:
     * - "PACKAGE" hoặc "Trừ gói hội viên"
     * - "DIRECT" hoặc "Thanh toán trực tiếp"
     */
    @NotBlank(message = "Vui lòng chọn hình thức thanh toán ('PACKAGE' hoặc 'DIRECT')")
    private String paymentMethod;

    /**
     * Kênh thanh toán trực tiếp (nếu paymentMethod là DIRECT):
     * "Bank_Transfer", "Cash", "Card", "E_Wallet"
     */
    private String paymentChannel;
}
