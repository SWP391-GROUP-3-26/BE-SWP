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

/**
 * Sport mà Member đã chọn từ một Choice Group khi mua Membership.
 *
 * <p>Chỉ lưu Selected Entitlements (chọn từ Choice Group).
 * Included Sports (được Package bao gồm sẵn) KHÔNG được lưu ở đây.</p>
 *
 * <p>Selection này phải được cố định cho toàn bộ thời hạn Membership.
 * Không được suy diễn lại từ Package hiện tại sau khi Membership đã tạo.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Membership_Sport_Selection", schema = "dbo")
public class MembershipSportSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Selection_ID")
    private Integer id;

    /**
     * Membership (Subscription) mà selection này thuộc về.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Subscription_ID", nullable = false)
    private MemberSubscription subscription;

    /**
     * Choice Group mà Member đã chọn Sport này từ.
     * Lưu lại để truy vết lịch sử, dù Choice Group có thể thay đổi sau này.
     */
    @Column(name = "Choice_Group_ID", nullable = false)
    private Integer choiceGroupId;

    /** Tên nhóm tại thời điểm mua, không bị ảnh hưởng khi Package được sửa. */
    @Column(name = "Choice_Group_Name_At_Purchase", nullable = false, length = 100)
    private String choiceGroupNameAtPurchase;

    /**
     * Sport (Subject) mà Member đã chọn.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Subject_ID", nullable = false)
    private Subject subject;

    @Column(name = "Subject_Name_At_Purchase", nullable = false, length = 100)
    private String subjectNameAtPurchase;
}
