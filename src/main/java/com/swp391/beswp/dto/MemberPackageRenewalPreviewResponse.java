package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Thông tin để Member xác nhận trước khi thanh toán gia hạn. */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberPackageRenewalPreviewResponse {
    private boolean success;
    private String message;
    private MemberPackageSubscriptionData currentSubscription;
    private PackageResponse.PackageData selectedPackage;
    private String renewalStart;
    private LocalDate newStartDate;
    private LocalDate newEndDate;
    private BigDecimal listedPrice;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
}
