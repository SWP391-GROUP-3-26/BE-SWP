package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** Dữ liệu cho màn hình Gói dịch vụ của Member. */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberPackageRenewalOverviewResponse {
    private boolean success;
    private String message;
    private MemberPackageSubscriptionData currentSubscription;
    private List<PackageResponse.PackageData> availablePackages;
}
