package com.swp391.beswp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Membership_Package", schema = "dbo")
public class MembershipPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Package_ID")
    private Integer id;

    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "Price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * Thời gian hiệu lực tính bằng số ngày.
     * VD: 1 tháng = 30 ngày, 3 tháng = 90 ngày, 12 tháng = 365 ngày.
     */
    @Column(name = "Duration", nullable = false)
    private Integer duration;

    @Column(name = "Included_Classes", nullable = false)
    @Builder.Default
    private Integer includedClasses = 0;

    @Column(name = "Benefits", columnDefinition = "NVARCHAR(MAX)")
    private String benefits;

    @Column(name = "Term_Conditions", columnDefinition = "NVARCHAR(MAX)")
    private String termConditions;

    /**
     * Trạng thái: 'Active' (Đang mở bán) hoặc 'Inactive' (Tạm ngưng cung cấp).
     */
    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "Active";
}
