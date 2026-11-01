package com.swp391.beswp.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

public final class SessionEvaluationDtos {
    private SessionEvaluationDtos() { }
    public record SaveEvaluation(
            @NotBlank @Size(max = 2000) String trainingContent,
            @NotBlank @Size(max = 2000) String result,
            @NotBlank @Pattern(regexp = "INITIAL|IMPROVING|STEADY|NEEDS_IMPROVEMENT") String progressStatus,
            @NotBlank @Size(max = 2000) String coachFeedback,
            @Size(max = 2000) String nextSteps,
            @PositiveOrZero Long version) { }
    public record WithdrawEvaluation(@NotNull @PositiveOrZero Long version) { }
    public record EvaluationView(Integer id, Integer sessionId, Integer memberId, String memberName, String className,
            String coachName, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, String trainingContent,
            String result, String progressStatus, String coachFeedback, String nextSteps, LocalDateTime updatedAt,
            long version, boolean withdrawn) { }
    public record MemberSummary(Integer sessionId) { }
    public record StudentView(Integer memberId, String memberName, String attendanceStatus,
            EvaluationView evaluation, List<EvaluationView> history) { }
    public record CoachView(Integer sessionId, LocalDate date, LocalTime startTime, LocalTime endTime,
            boolean canEvaluate, List<StudentView> students) { }
    public record Result<T>(boolean success, String message, T data) {
        public static <T> Result<T> ok(T data) { return new Result<>(true, "Thành công", data); }
    }
}
