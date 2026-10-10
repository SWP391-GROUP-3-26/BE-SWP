package com.swp391.beswp.repository;

import com.swp391.beswp.dto.CenterManagerDashboardResponse.ActivityItem;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.BreakdownItem;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.RevenuePoint;
import com.swp391.beswp.dto.CenterManagerDashboardResponse.Summary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CenterManagerDashboardRepository {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final JdbcTemplate jdbcTemplate;

    public Summary getSummary(YearMonth currentMonth) {
        YearMonth previousMonth = currentMonth.minusMonths(1);
        LocalDateTime currentStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime previousStart = previousMonth.atDay(1).atStartOfDay();

        String sql = """
                SELECT
                    (SELECT COUNT_BIG(*) FROM [User] u JOIN Role r ON r.Role_ID = u.Role_ID
                     WHERE LOWER(LTRIM(RTRIM(r.Role_Name))) = N'member') AS total_members,
                    (SELECT COUNT_BIG(*) FROM [User] u JOIN Role r ON r.Role_ID = u.Role_ID
                     WHERE LOWER(LTRIM(RTRIM(r.Role_Name))) = N'member'
                       AND LOWER(LTRIM(RTRIM(u.Status))) = N'active') AS active_members,
                    (SELECT COUNT_BIG(*) FROM [User] u JOIN Role r ON r.Role_ID = u.Role_ID
                     WHERE LOWER(LTRIM(RTRIM(r.Role_Name))) = N'receptionist') AS total_receptionists,
                    (SELECT COUNT_BIG(*) FROM [User] u JOIN Role r ON r.Role_ID = u.Role_ID
                     WHERE LOWER(LTRIM(RTRIM(r.Role_Name))) = N'receptionist'
                       AND LOWER(LTRIM(RTRIM(u.Status))) = N'active') AS active_receptionists,
                    (SELECT COUNT_BIG(*) FROM [User] u JOIN Role r ON r.Role_ID = u.Role_ID
                     WHERE LOWER(LTRIM(RTRIM(r.Role_Name))) = N'coach') AS total_coaches,
                    (SELECT COUNT_BIG(*) FROM [User] u JOIN Role r ON r.Role_ID = u.Role_ID
                     WHERE LOWER(LTRIM(RTRIM(r.Role_Name))) = N'coach'
                       AND LOWER(LTRIM(RTRIM(u.Status))) = N'active') AS active_coaches,
                    (SELECT COUNT_BIG(*) FROM [User] u JOIN Role r ON r.Role_ID = u.Role_ID
                     WHERE LOWER(LTRIM(RTRIM(r.Role_Name))) IN (N'center manager', N'admin')) AS total_center_managers,
                    (SELECT COUNT_BIG(*) FROM [User] u JOIN Role r ON r.Role_ID = u.Role_ID
                     WHERE LOWER(LTRIM(RTRIM(r.Role_Name))) IN (N'center manager', N'admin')
                       AND LOWER(LTRIM(RTRIM(u.Status))) = N'active') AS active_center_managers,
                    (SELECT COUNT_BIG(*) FROM Class) AS total_classes,
                    (SELECT COUNT_BIG(*) FROM Class
                     WHERE LOWER(LTRIM(RTRIM(Status))) IN (N'open', N'active')) AS active_classes,
                    (SELECT COUNT_BIG(*) FROM Membership_Package) AS total_packages,
                    (SELECT COUNT_BIG(*) FROM Membership_Package
                     WHERE LOWER(LTRIM(RTRIM(Status))) = N'active') AS active_packages,
                    (SELECT COUNT_BIG(*) FROM Member_Subscription
                     WHERE LOWER(LTRIM(RTRIM(Status))) = N'active') AS active_subscriptions,
                    COALESCE((SELECT SUM(Amount) FROM Payment
                     WHERE LOWER(LTRIM(RTRIM(Status))) = N'paid'
                       AND Payment_Date >= ? AND Payment_Date < ?), CAST(0 AS DECIMAL(12,2))) AS revenue_this_month,
                    COALESCE((SELECT SUM(Amount) FROM Payment
                     WHERE LOWER(LTRIM(RTRIM(Status))) = N'paid'
                       AND Payment_Date >= ? AND Payment_Date < ?), CAST(0 AS DECIMAL(12,2))) AS revenue_previous_month
                """;

        return jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> {
            BigDecimal currentRevenue = money(resultSet.getBigDecimal("revenue_this_month"));
            BigDecimal previousRevenue = money(resultSet.getBigDecimal("revenue_previous_month"));
            return new Summary(
                    resultSet.getLong("total_members"),
                    resultSet.getLong("active_members"),
                    resultSet.getLong("total_receptionists"),
                    resultSet.getLong("active_receptionists"),
                    resultSet.getLong("total_coaches"),
                    resultSet.getLong("active_coaches"),
                    resultSet.getLong("total_center_managers"),
                    resultSet.getLong("active_center_managers"),
                    resultSet.getLong("total_classes"),
                    resultSet.getLong("active_classes"),
                    resultSet.getLong("total_packages"),
                    resultSet.getLong("active_packages"),
                    resultSet.getLong("active_subscriptions"),
                    currentRevenue,
                    previousRevenue,
                    percentChange(currentRevenue, previousRevenue)
            );
        }, Timestamp.valueOf(currentStart), Timestamp.valueOf(nextMonthStart),
                Timestamp.valueOf(previousStart), Timestamp.valueOf(currentStart));
    }

    public List<RevenuePoint> getRevenue(YearMonth currentMonth, int months) {
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        LocalDateTime from = firstMonth.atDay(1).atStartOfDay();
        LocalDateTime until = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        String sql = """
                SELECT YEAR(Payment_Date) AS payment_year,
                       MONTH(Payment_Date) AS payment_month,
                       COALESCE(SUM(Amount), CAST(0 AS DECIMAL(12,2))) AS revenue,
                       COUNT_BIG(*) AS paid_payment_count
                FROM Payment
                WHERE LOWER(LTRIM(RTRIM(Status))) = N'paid'
                  AND Payment_Date >= ? AND Payment_Date < ?
                GROUP BY YEAR(Payment_Date), MONTH(Payment_Date)
                ORDER BY payment_year, payment_month
                """;

        Map<YearMonth, RevenuePoint> pointsByMonth = new HashMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            YearMonth month = YearMonth.of(resultSet.getInt("payment_year"), resultSet.getInt("payment_month"));
            pointsByMonth.put(month, new RevenuePoint(
                    month.toString(),
                    money(resultSet.getBigDecimal("revenue")),
                    resultSet.getLong("paid_payment_count")
            ));
        }, Timestamp.valueOf(from), Timestamp.valueOf(until));

        List<RevenuePoint> points = new ArrayList<>(months);
        for (int offset = 0; offset < months; offset++) {
            YearMonth month = firstMonth.plusMonths(offset);
            points.add(pointsByMonth.getOrDefault(month, new RevenuePoint(month.toString(), ZERO_MONEY, 0)));
        }
        return points;
    }

    public List<BreakdownItem> getClassStatuses() {
        return jdbcTemplate.query("""
                SELECT LOWER(LTRIM(RTRIM(Status))) AS status, COUNT_BIG(*) AS item_count
                FROM Class
                GROUP BY LOWER(LTRIM(RTRIM(Status)))
                ORDER BY status
                """, (resultSet, rowNumber) -> new BreakdownItem(
                resultSet.getString("status"), resultSet.getLong("item_count")));
    }

    public List<BreakdownItem> getSubscriptionStatuses() {
        return jdbcTemplate.query("""
                SELECT LOWER(LTRIM(RTRIM(Status))) AS status, COUNT_BIG(*) AS item_count
                FROM Member_Subscription
                GROUP BY LOWER(LTRIM(RTRIM(Status)))
                ORDER BY status
                """, (resultSet, rowNumber) -> new BreakdownItem(
                resultSet.getString("status"), resultSet.getLong("item_count")));
    }

    public List<ActivityItem> getRecentActivities(int limit) {
        return jdbcTemplate.query("""
                SELECT al.Log_ID, al.User_ID,
                       COALESCE(NULLIF(LTRIM(RTRIM(u.FullName)), N''), u.Username, N'Unknown') AS actor_name,
                       al.Action_Type, al.Target_Entity, al.Description, al.Created_At
                FROM Activity_Log al
                LEFT JOIN [User] u ON u.User_ID = al.User_ID
                ORDER BY al.Created_At DESC, al.Log_ID DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """, (resultSet, rowNumber) -> {
            Timestamp createdAt = resultSet.getTimestamp("Created_At");
            return new ActivityItem(
                    resultSet.getLong("Log_ID"),
                    resultSet.getObject("User_ID", Integer.class),
                    resultSet.getString("actor_name"),
                    resultSet.getString("Action_Type"),
                    resultSet.getString("Target_Entity"),
                    resultSet.getString("Description"),
                    createdAt == null ? null : createdAt.toLocalDateTime()
            );
        }, limit);
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal amount) {
        return amount == null ? ZERO_MONEY : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
