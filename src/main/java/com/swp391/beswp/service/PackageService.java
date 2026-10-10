package com.swp391.beswp.service;

import com.swp391.beswp.dto.CreatePackageRequest;
import com.swp391.beswp.dto.PackageListResponse;
import com.swp391.beswp.dto.PackageResponse;
import com.swp391.beswp.dto.UpdatePackageRequest;
import com.swp391.beswp.entity.MembershipPackage;
import com.swp391.beswp.repository.MembershipPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PackageService {

    private final MembershipPackageRepository packageRepository;

    // ------------------------------------------------------------------ //
    //  Tạo gói dịch vụ mới
    // ------------------------------------------------------------------ //

    @Transactional
    public PackageResponse createPackage(CreatePackageRequest request) {
        String name = normalize(request.getName());
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tên gói dịch vụ không được để trống");
        }

        if (packageRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tên gói dịch vụ '" + name + "' đã tồn tại trong hệ thống");
        }

        validateStatus(request.getStatus());

        int durationDays = monthsToDays(request.getDurationMonths());

        MembershipPackage pkg = MembershipPackage.builder()
                .name(name)
                .description(request.getDescription())
                .price(request.getPrice())
                .duration(durationDays)
                .includedClasses(
                        request.getIncludedClasses() != null ? request.getIncludedClasses() : 0)
                .benefits(request.getBenefits())
                .termConditions(request.getTermConditions())
                .status(resolveStatus(request.getStatus()))
                .build();

        MembershipPackage saved = packageRepository.save(pkg);
        return PackageResponse.success("Tạo mới gói dịch vụ thành công", saved);
    }

    // ------------------------------------------------------------------ //
    //  Lấy danh sách gói dịch vụ (có tìm kiếm & lọc trạng thái)
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public PackageListResponse getAllPackages(String search, String status) {
        String keyword = normalize(search);
        String st = normalize(status);

        // "all" hoặc "tất cả" → không lọc theo trạng thái
        if ("all".equalsIgnoreCase(st) || "tất cả".equalsIgnoreCase(st)) {
            st = null;
        }

        List<MembershipPackage> packages = packageRepository.searchPackages(
                StringUtils.hasText(keyword) ? keyword : null,
                StringUtils.hasText(st) ? st : null
        );
        return PackageListResponse.success(packages);
    }

    // ------------------------------------------------------------------ //
    //  Lấy chi tiết một gói theo ID
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public PackageResponse getPackageById(Integer id) {
        MembershipPackage pkg = findOrThrow(id);
        return PackageResponse.success("Lấy thông tin gói dịch vụ thành công", pkg);
    }

    // ------------------------------------------------------------------ //
    //  Cập nhật gói dịch vụ
    // ------------------------------------------------------------------ //

    @Transactional
    public PackageResponse updatePackage(Integer id, UpdatePackageRequest request) {
        MembershipPackage pkg = findOrThrow(id);

        if (StringUtils.hasText(request.getName())) {
            String newName = normalize(request.getName());
            if (!newName.equalsIgnoreCase(pkg.getName())
                    && packageRepository.existsByNameIgnoreCase(newName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Tên gói dịch vụ '" + newName + "' đã tồn tại trong hệ thống");
            }
            pkg.setName(newName);
        }

        if (request.getDescription() != null) {
            pkg.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            pkg.setPrice(request.getPrice());
        }
        if (request.getDurationMonths() != null) {
            pkg.setDuration(monthsToDays(request.getDurationMonths()));
        }
        if (request.getIncludedClasses() != null) {
            pkg.setIncludedClasses(request.getIncludedClasses());
        }
        if (request.getBenefits() != null) {
            pkg.setBenefits(request.getBenefits());
        }
        if (request.getTermConditions() != null) {
            pkg.setTermConditions(request.getTermConditions());
        }
        if (StringUtils.hasText(request.getStatus())) {
            validateStatus(request.getStatus());
            pkg.setStatus(request.getStatus().trim());
        }

        MembershipPackage updated = packageRepository.save(pkg);
        return PackageResponse.success("Cập nhật gói dịch vụ thành công", updated);
    }

    // ------------------------------------------------------------------ //
    //  Xoá gói dịch vụ
    // ------------------------------------------------------------------ //

    @Transactional
    public void deletePackage(Integer id) {
        MembershipPackage pkg = findOrThrow(id);
        packageRepository.delete(pkg);
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    /**
     * Quy đổi số tháng → số ngày.
     * Công thức: tháng × 30, ngoại trừ 12 tháng = 365 ngày và 24 tháng = 730 ngày.
     */
    private int monthsToDays(Integer months) {
        if (months == null || months <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thời gian hiệu lực phải lớn hơn 0 tháng");
        }
        return switch (months) {
            case 12 -> 365;
            case 24 -> 730;
            default -> months * 30;
        };
    }

    private void validateStatus(String status) {
        if (status != null && !status.trim().isEmpty()
                && !status.trim().equalsIgnoreCase("Active")
                && !status.trim().equalsIgnoreCase("Inactive")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái chỉ được là 'Active' (Đang mở bán) hoặc 'Inactive' (Tạm ngưng cung cấp)");
        }
    }

    private String resolveStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : "Active";
    }

    private MembershipPackage findOrThrow(Integer id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy gói dịch vụ với ID: " + id));
    }

    private String normalize(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
