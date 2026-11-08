package com.swp391.beswp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MembershipFreezeResponse(boolean success, Integer subscriptionId, String status,
        int totalFreezeMonths, int usedFreezeMonths, int remainingFreezeMonths,
        LocalDateTime freezeStartedAt, LocalDateTime freezeEndsAt, LocalDate endDate,
        long extendedDays, String freezeTerms) { }
