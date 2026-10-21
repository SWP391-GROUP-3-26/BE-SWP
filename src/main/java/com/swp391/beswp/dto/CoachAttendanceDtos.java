package com.swp391.beswp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class CoachAttendanceDtos {

    private CoachAttendanceDtos() {
    }

    public record SessionsResponse(
            boolean success,
            String message,
            List<SessionSummary> data
    ) {
    }

    public record AttendanceResponse(
            boolean success,
            String message,
            SessionAttendance data
    ) {
    }

    public record SessionSummary(
            Integer sessionId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String status,
            int registeredMembers,
            int markedMembers,
            int presentCount,
            int absentCount,
            int lateCount,
            int excusedCount
    ) {
    }

    public record SessionAttendance(
            SessionSummary session,
            List<MemberAttendance> members
    ) {
    }

    public record MemberAttendance(
            Integer memberId,
            String fullName,
            String username,
            String phone,
            String avatarUrl,
            String status,
            String note
    ) {
    }

    public record SaveAttendanceRequest(
            @NotEmpty(message = "Danh sách điểm danh không được để trống")
            @Size(max = 500, message = "Danh sách điểm danh vượt quá giới hạn")
            List<@Valid AttendanceEntry> records
    ) {
    }

    public record AttendanceEntry(
            @NotNull(message = "memberId không được để trống")
            @Positive(message = "memberId phải là số nguyên dương")
            Integer memberId,

            @NotBlank(message = "Trạng thái điểm danh không được để trống")
            @Pattern(
                    regexp = "(?i)^(Present|Absent|Late|Excused)$",
                    message = "Trạng thái chỉ nhận Present, Absent, Late hoặc Excused"
            )
            String status,

            @Size(max = 255, message = "Ghi chú không được vượt quá 255 ký tự")
            String note
    ) {
    }
}
