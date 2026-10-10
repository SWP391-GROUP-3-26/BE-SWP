package com.swp391.beswp.dto;

import com.swp391.beswp.entity.MembershipPackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PackageResponse {

    private boolean success;
    private String message;
    private PackageData data;

    public static PackageResponse success(String message, MembershipPackage pkg) {
        return new PackageResponse(true, message, PackageData.fromEntity(pkg));
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PackageData {

        /** Mã gói định dạng PKG-001, PKG-002, ... */
        private String code;
        private Integer id;
        private String name;
        private String description;
        private BigDecimal price;

        /** Thời gian hiệu lực lưu theo số ngày trong DB */
        private Integer durationDays;

        /**
         * Thời gian hiệu lực quy đổi sang tháng (để FE hiển thị).
         * Dùng công thức làm tròn: tháng = round(days / 30).
         */
        private Integer durationMonths;

        private Integer includedClasses;
        private String benefits;
        private String termConditions;
        private String status;
        private String statusLabel;

        public static PackageData fromEntity(MembershipPackage pkg) {
            if (pkg == null) return null;

            int days = pkg.getDuration() != null ? pkg.getDuration() : 0;
            int months = (int) Math.round(days / 30.0);

            String statusLabel = "Active".equalsIgnoreCase(pkg.getStatus())
                    ? "Đang mở bán"
                    : "Tạm ngưng cung cấp";

            return PackageData.builder()
                    .id(pkg.getId())
                    .code(String.format("PKG-%02d", pkg.getId()))
                    .name(pkg.getName())
                    .description(pkg.getDescription())
                    .price(pkg.getPrice())
                    .durationDays(days)
                    .durationMonths(months)
                    .includedClasses(pkg.getIncludedClasses())
                    .benefits(pkg.getBenefits())
                    .termConditions(pkg.getTermConditions())
                    .status(pkg.getStatus())
                    .statusLabel(statusLabel)
                    .build();
        }
    }
}
