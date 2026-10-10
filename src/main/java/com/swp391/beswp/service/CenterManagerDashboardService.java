package com.swp391.beswp.service;

import com.swp391.beswp.dto.CenterManagerDashboardResponse.ActivityItem;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.BreakdownItem;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.Overview;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.RevenuePoint;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.Summary;
import com.swp391.beswp.repository.CenterManagerDashboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CenterManagerDashboardService {

    private static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CenterManagerDashboardRepository dashboardRepository;

    @Transactional(readOnly = true)
    public Overview getOverview(int months, int activityLimit) {
        YearMonth currentMonth = currentMonth();
        return new Overview(
                LocalDate.now(BUSINESS_TIME_ZONE),
                dashboardRepository.getSummary(currentMonth),
                dashboardRepository.getRevenue(currentMonth, months),
                dashboardRepository.getClassStatuses(),
                dashboardRepository.getSubscriptionStatuses(),
                dashboardRepository.getRecentActivities(activityLimit)
        );
    }

    @Transactional(readOnly = true)
    public Summary getSummary() {
        return dashboardRepository.getSummary(currentMonth());
    }

    @Transactional(readOnly = true)
    public List<RevenuePoint> getRevenue(int months) {
        return dashboardRepository.getRevenue(currentMonth(), months);
    }

    @Transactional(readOnly = true)
    public List<ActivityItem> getRecentActivities(int limit) {
        return dashboardRepository.getRecentActivities(limit);
    }

    @Transactional(readOnly = true)
    public List<BreakdownItem> getClassStatuses() {
        return dashboardRepository.getClassStatuses();
    }

    @Transactional(readOnly = true)
    public List<BreakdownItem> getSubscriptionStatuses() {
        return dashboardRepository.getSubscriptionStatuses();
    }

    private YearMonth currentMonth() {
        return YearMonth.now(BUSINESS_TIME_ZONE);
    }
}
