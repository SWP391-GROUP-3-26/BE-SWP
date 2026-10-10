package com.swp391.beswp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CenterManagerDashboardResponse<T>(
        boolean success,
        String message,
        T data
) {

    public static <T> CenterManagerDashboardResponse<T> success(String message, T data) {
        return new CenterManagerDashboardResponse<>(true, message, data);
    }

    public record Overview(
            LocalDate asOfDate,
            Summary summary,
            List<RevenuePoint> revenue,
            List<BreakdownItem> classStatuses,
            List<BreakdownItem> subscriptionStatuses,
            List<ActivityItem> recentActivities
    ) {
    }

    public record Summary(
            long totalMembers,
            long activeMembers,
            long totalReceptionists,
            long activeReceptionists,
            long totalCoaches,
            long activeCoaches,
            long totalCenterManagers,
            long activeCenterManagers,
            long totalClasses,
            long activeClasses,
            long totalPackages,
            long activePackages,
            long activeSubscriptions,
            BigDecimal revenueThisMonth,
            BigDecimal revenuePreviousMonth,
            BigDecimal revenueChangePercent
    ) {
    }

    public record RevenuePoint(
            String month,
            BigDecimal revenue,
            long paidPaymentCount
    ) {
    }

    public record BreakdownItem(String status, long count) {
    }

    public record ActivityItem(
            long id,
            Integer userId,
            String actorName,
            String actionType,
            String targetEntity,
            String description,
            LocalDateTime createdAt
    ) {
    }
}
