package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MemberPackageSubscriptionListResponse {

    private boolean success;
    private String message;
    private long total;
    private int page;
    private int size;
    private List<MemberPackageSubscriptionData> data;
}
