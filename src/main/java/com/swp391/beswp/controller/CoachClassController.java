package com.swp391.beswp.controller;

import com.swp391.beswp.dto.CoachClassDetailResponse;
import com.swp391.beswp.dto.CoachClassListResponse;
import com.swp391.beswp.service.CoachClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Coach Classes", description = "API Quản lý danh sách lớp học của Huấn luyện viên (Coach)")
@RestController
@RequestMapping("/api/coach/classes")
@RequiredArgsConstructor
public class CoachClassController {

    private final CoachClassService coachClassService;

    @Operation(summary = "Xem danh sách lớp đang dạy của Coach", description = "Trả về danh sách các lớp học mà Huấn luyện viên đang phụ trách, hỗ trợ tìm kiếm và lọc theo trạng thái kèm số lượng thống kê.")
    @GetMapping
    public ResponseEntity<CoachClassListResponse> getMyClasses(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(coachClassService.getCoachClasses(authentication.getName(), status, search));
    }

    @Operation(summary = "Xem chi tiết lớp học và danh sách học viên", description = "Trả về thông tin chi tiết một lớp học cùng danh sách toàn bộ học viên đang theo học lớp này.")
    @GetMapping("/{classId}")
    public ResponseEntity<CoachClassDetailResponse> getClassDetail(
            Authentication authentication,
            @PathVariable Integer classId
    ) {
        return ResponseEntity.ok(coachClassService.getCoachClassDetail(authentication.getName(), classId));
    }
}
