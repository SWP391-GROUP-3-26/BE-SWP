package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailableClassListResponse {

    private boolean success;
    private String message;
    private MemberSubscriptionInfoResponse activeSubscription;
    private List<String> categories;
    private int totalAvailableClasses;
    private List<AvailableClassCardResponse> classes;
}
