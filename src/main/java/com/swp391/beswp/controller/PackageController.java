package com.swp391.beswp.controller;

import com.swp391.beswp.dto.CreatePackageRequest;
import com.swp391.beswp.dto.PackageListResponse;
import com.swp391.beswp.dto.PackageResponse;
import com.swp391.beswp.dto.UpdatePackageRequest;
import com.swp391.beswp.service.PackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class PackageController {

    private final PackageService packageService;

    /**
     * POST /api/packages
     * Tạo mới một gói dịch vụ.
     */
    @PostMapping
    public ResponseEntity<PackageResponse> createPackage(
            @Valid @RequestBody CreatePackageRequest request) {
        PackageResponse response = packageService.createPackage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/packages?search=&status=
     * Lấy danh sách gói dịch vụ, hỗ trợ tìm kiếm và lọc trạng thái.
     */
    @GetMapping
    public ResponseEntity<PackageListResponse> getAllPackages(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(packageService.getAllPackages(search, status));
    }

    /**
     * GET /api/packages/{id}
     * Lấy chi tiết một gói dịch vụ theo ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PackageResponse> getPackageById(@PathVariable Integer id) {
        return ResponseEntity.ok(packageService.getPackageById(id));
    }

    /**
     * PUT /api/packages/{id}
     * Cập nhật thông tin gói dịch vụ (chỉ cập nhật các trường được truyền).
     */
    @PutMapping("/{id}")
    public ResponseEntity<PackageResponse> updatePackage(
            @PathVariable Integer id,
            @Valid @RequestBody UpdatePackageRequest request) {
        return ResponseEntity.ok(packageService.updatePackage(id, request));
    }

    /**
     * DELETE /api/packages/{id}
     * Xoá gói dịch vụ theo ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePackage(@PathVariable Integer id) {
        packageService.deletePackage(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Xoá gói dịch vụ thành công"
        ));
    }
}
