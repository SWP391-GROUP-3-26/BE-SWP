package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MemberPackageSubscriptionResponse {

    private boolean success;
    private String message;
    private MemberPackageSubscriptionData data;
}
