package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassConfirmationDetailResponse {

    private boolean success;
    private String message;

    private Integer classId;
    private String classCode;
    private String className;
    private String schedule;
    private String roomName;
    private String coachName;
    private BigDecimal price;
    private String priceFormatted;
    private String description;

    private PackagePaymentOption packageOption;
    private DirectPaymentOption directPaymentOption;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PackagePaymentOption {
        private boolean eligible;
        private String title;
        private String subtitle;
        private Integer remainingSessions;
        private String packageName;
        private String note;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DirectPaymentOption {
        private boolean available;
        private String title;
        private String subtitle;
        private BigDecimal amount;
        private String amountFormatted;
    }
}
