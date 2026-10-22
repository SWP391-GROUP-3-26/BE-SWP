package com.swp391.beswp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Membership Record — một lần mua gói hội viên của Member.
 *
 * <h3>Trạng thái (Section 21)</h3>
 * <pre>
 * PENDING  → ACTIVE    (khi đến start_date hoặc ngay lập tức nếu Start Immediately)
 * ACTIVE   → EXPIRED   (khi đến end_date, do system tự động)
 * ACTIVE   → CANCELLED (do Center Manager "Ngừng cung cấp dịch vụ / Hủy hợp đồng")
 * </pre>
 *
 * <h3>Snapshot (Section 19)</h3>
 * Các trường {@code priceAtPurchase}, {@code durationAtPurchase}, {@code termsAtPurchase}
 * lưu lại thông tin tại thời điểm mua, đảm bảo quyền lợi không thay đổi dù Package bị chỉnh sửa sau này.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Member_Subscription", schema = "dbo")
public class MemberSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Subscription_ID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "User_ID", nullable = false)
    private User user;

    /**
     * FK đến Package được mua. Dùng để tra cứu, nhưng quyền lợi PHẢI đọc từ snapshot.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Package_ID", nullable = false)
    private MembershipPackage membershipPackage;

    // ── Snapshot tại thời điểm mua ──────────────────────────────────────────

    /** Giá tại thời điểm mua (sau chiết khấu nếu có). */
    @Column(name = "Price_At_Purchase", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtPurchase;

    /** Thời hạn (ngày) tại thời điểm mua. */
    @Column(name = "Duration_At_Purchase", nullable = false)
    private Integer durationAtPurchase;

    /** Điều khoản tại thời điểm mua. */
    @Column(name = "Terms_At_Purchase", columnDefinition = "NVARCHAR(MAX)")
    private String termsAtPurchase;

    /** Tên Package tại thời điểm mua (để hiển thị lịch sử dù Package bị đổi tên). */
    @Column(name = "Package_Name_At_Purchase", nullable = false, length = 200)
    private String packageNameAtPurchase;

    // ── Thời gian hiệu lực ──────────────────────────────────────────────────

    @Column(name = "Start_Date", nullable = false)
    private LocalDate startDate;

    @Column(name = "End_Date", nullable = false)
    private LocalDate endDate;

    // ── Trạng thái ──────────────────────────────────────────────────────────

    /**
     * Trạng thái Membership: PENDING | ACTIVE | EXPIRED | CANCELLED.
     * Được System quản lý — không chỉnh sửa trực tiếp ngoài service.
     */
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "Pending";

    /**
     * Lý do hủy (khi Center Manager thực hiện "Ngừng cung cấp dịch vụ").
     * Bắt buộc ghi lại khi status = CANCELLED.
     */
    @Column(name = "Cancellation_Reason", columnDefinition = "NVARCHAR(MAX)")
    private String cancellationReason;

    /** Thời điểm bị hủy. */
    @Column(name = "Cancelled_At")
    private LocalDateTime cancelledAt;

    /** Thời điểm tạo record. */
    @Column(name = "Created_At", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
