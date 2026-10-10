package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberSubscriptionInfoResponse {

    private boolean hasActivePackage;
    private Integer subscriptionId;
    private Integer packageId;
    private String packageName;
    private Integer remainingSessions;
    private Integer totalSessions;
    private Integer usedSessions;
    private LocalDate startDate;
    private LocalDate endDate;
    private String displayBanner;
}
