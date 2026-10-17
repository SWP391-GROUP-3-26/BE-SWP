package com.swp391.beswp.controller;

import com.swp391.beswp.dto.AuditLogDetailResponse;
import com.swp391.beswp.dto.AuditLogListResponse;
import com.swp391.beswp.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Audit Log", description = "API Nhật ký kiểm toán dành cho Admin và Center Manager")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Xem danh sách Nhật ký kiểm toán (Audit Log)", description = "Lấy danh sách nhật ký kiểm toán với các bộ lọc phân loại (Class, Package, User...), hành động (Thêm mới, Cập nhật, Xóa dữ liệu), khoảng ngày và từ khóa tìm kiếm.")
    @GetMapping
    public ResponseEntity<AuditLogListResponse> getAuditLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(auditLogService.getAuditLogs(category, action, search, startDate, endDate, page, size));
    }

    @Operation(summary = "Xem chi tiết một bản ghi Nhật ký kiểm toán", description = "Lấy thông tin chi tiết của một bản ghi nhật ký kiểm toán theo ID.")
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDetailResponse> getAuditLogDetail(@PathVariable Long id) {
        return ResponseEntity.ok(auditLogService.getAuditLogDetail(id));
    }
}
