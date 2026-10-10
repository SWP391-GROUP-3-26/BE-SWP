package com.swp391.beswp.dto;

import com.swp391.beswp.entity.MembershipPackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PackageListResponse {

    private boolean success;
    private String message;
    private int total;
    private List<PackageResponse.PackageData> data;

    public static PackageListResponse success(List<MembershipPackage> packages) {
        List<PackageResponse.PackageData> list = packages.stream()
                .map(PackageResponse.PackageData::fromEntity)
                .toList();
        return new PackageListResponse(true, "Lấy danh sách gói dịch vụ thành công", list.size(), list);
    }
}
