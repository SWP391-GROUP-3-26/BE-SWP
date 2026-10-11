package com.swp391.beswp.dto;

import com.swp391.beswp.entity.MembershipPackage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MemberPackageCatalogResponse {

    private boolean success;
    private String message;
    private long total;
    private int page;
    private int size;
    private List<PackageResponse.PackageData> data;

    public static MemberPackageCatalogResponse success(
            List<MembershipPackage> packages,
            long total,
            int page,
            int size
    ) {
        List<PackageResponse.PackageData> items = packages.stream()
                .map(PackageResponse.PackageData::fromEntity)
                .toList();
        return new MemberPackageCatalogResponse(
                true,
                "Lấy danh sách gói đang mở bán thành công",
                total,
                page,
                size,
                items
        );
    }
}
