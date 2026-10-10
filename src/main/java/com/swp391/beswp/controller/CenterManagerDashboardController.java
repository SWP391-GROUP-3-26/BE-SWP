package com.swp391.beswp.controller;

import com.swp391.beswp.dto.CenterManagerDashboardResponse;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.ActivityItem;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.BreakdownItem;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.Overview;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.RevenuePoint;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.Summary;
import com.swp391.beswp.service.CenterManagerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping({"/api/center-manager/dashboard", "/api/admin/dashboard"})
@RequiredArgsConstructor
public class CenterManagerDashboardController {

    private static final int MAX_REVENUE_MONTHS = 24;
    private static final int MAX_ACTIVITY_LIMIT = 100;

    private final CenterManagerDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<CenterManagerDashboardResponse<Overview>> getOverview(
            Authentication authentication,
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(defaultValue = "10") int activityLimit
    ) {
        requireDashboardRole(authentication);
        validateRange(months, 1, MAX_REVENUE_MONTHS, "months");
        validateRange(activityLimit, 1, MAX_ACTIVITY_LIMIT, "activityLimit");
        return ResponseEntity.ok(CenterManagerDashboardResponse.success(
                "Lấy tổng quan dashboard thành công",
                dashboardService.getOverview(months, activityLimit)
        ));
    }

    @GetMapping("/summary")
    public ResponseEntity<CenterManagerDashboardResponse<Summary>> getSummary(Authentication authentication) {
        requireDashboardRole(authentication);
        return ResponseEntity.ok(CenterManagerDashboardResponse.success(
                "Lấy chỉ số tổng quan thành công", dashboardService.getSummary()));
    }

    @GetMapping("/revenue")
    public ResponseEntity<CenterManagerDashboardResponse<List<RevenuePoint>>> getRevenue(
            Authentication authentication,
            @RequestParam(defaultValue = "6") int months
    ) {
        requireDashboardRole(authentication);
        validateRange(months, 1, MAX_REVENUE_MONTHS, "months");
        return ResponseEntity.ok(CenterManagerDashboardResponse.success(
                "Lấy báo cáo doanh thu thành công", dashboardService.getRevenue(months)));
    }

    @GetMapping("/activities")
    public ResponseEntity<CenterManagerDashboardResponse<List<ActivityItem>>> getRecentActivities(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit
    ) {
        requireDashboardRole(authentication);
        validateRange(limit, 1, MAX_ACTIVITY_LIMIT, "limit");
        return ResponseEntity.ok(CenterManagerDashboardResponse.success(
                "Lấy hoạt động gần đây thành công", dashboardService.getRecentActivities(limit)));
    }

    @GetMapping("/class-statuses")
    public ResponseEntity<CenterManagerDashboardResponse<List<BreakdownItem>>> getClassStatuses(
            Authentication authentication
    ) {
        requireDashboardRole(authentication);
        return ResponseEntity.ok(CenterManagerDashboardResponse.success(
                "Lấy thống kê trạng thái lớp học thành công", dashboardService.getClassStatuses()));
    }

    @GetMapping("/subscription-statuses")
    public ResponseEntity<CenterManagerDashboardResponse<List<BreakdownItem>>> getSubscriptionStatuses(
            Authentication authentication
    ) {
        requireDashboardRole(authentication);
        return ResponseEntity.ok(CenterManagerDashboardResponse.success(
                "Lấy thống kê trạng thái gói hội viên thành công", dashboardService.getSubscriptionStatuses()));
    }

    private void requireDashboardRole(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean allowed = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_Center Manager".equals(authority.getAuthority())
                        || "ROLE_Admin".equals(authority.getAuthority()));
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Center Manager role required");
        }
    }

    private void validateRange(int value, int minimum, int maximum, String parameterName) {
        if (value < minimum || value > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    parameterName + " must be between " + minimum + " and " + maximum);
        }
    }
}
