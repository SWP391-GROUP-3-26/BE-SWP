package com.swp391.beswp.controller;

import com.swp391.beswp.dto.CoachAttendanceDtos.AttendanceResponse;
import com.swp391.beswp.dto.CoachAttendanceDtos.SaveAttendanceRequest;
import com.swp391.beswp.dto.CoachAttendanceDtos.SessionsResponse;
import com.swp391.beswp.service.CoachAttendanceService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coach/classes/{classId}/sessions")
@RequiredArgsConstructor
@Tag(name = "Coach Attendance", description = "API xem và lưu điểm danh hội viên theo buổi học")
public class CoachAttendanceController {

    private final CoachAttendanceService coachAttendanceService;

    @GetMapping
    @Operation(summary = "Danh sách buổi học", description = "Trả về các buổi của lớp Coach phụ trách cùng số hội viên và thống kê điểm danh.")
    public ResponseEntity<SessionsResponse> getClassSessions(
            Authentication authentication,
            @PathVariable Integer classId
    ) {
        return ResponseEntity.ok(coachAttendanceService.getClassSessions(authentication, classId));
    }

    @GetMapping("/{sessionId}/attendance")
    @Operation(summary = "Danh sách hội viên điểm danh", description = "Trả về roster gồm hội viên có booking Booked hoặc Completed, kèm trạng thái đã lưu nếu có.")
    public ResponseEntity<AttendanceResponse> getSessionAttendance(
            Authentication authentication,
            @PathVariable Integer classId,
            @PathVariable Integer sessionId
    ) {
        return ResponseEntity.ok(coachAttendanceService.getSessionAttendance(authentication, classId, sessionId));
    }

    @PutMapping("/{sessionId}/attendance")
    @Operation(summary = "Lưu điểm danh cả buổi", description = "Upsert điểm danh cho toàn bộ roster hiện tại. Mỗi memberId đang đăng ký phải xuất hiện đúng một lần; trạng thái nhận Present, Absent, Late hoặc Excused.")
    public ResponseEntity<AttendanceResponse> saveSessionAttendance(
            Authentication authentication,
            @PathVariable Integer classId,
            @PathVariable Integer sessionId,
            @Valid @RequestBody SaveAttendanceRequest request
    ) {
        return ResponseEntity.ok(coachAttendanceService.saveSessionAttendance(
                authentication, classId, sessionId, request));
    }
}
