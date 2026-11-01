package com.swp391.beswp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class TrainingPlanDtos {
    private TrainingPlanDtos() { }

    public record SavePlan(
            @NotNull @Positive Integer classId,
            @Positive Integer memberId,
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 2000) String goal,
            @Size(max = 2000) String assessment,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            boolean publish,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid ExerciseInput> exercises,
            @NotNull @PositiveOrZero Long version,
            @Pattern(regexp = "INDIVIDUAL|CLASS") String scope) {
        public SavePlan(Integer classId, Integer memberId, String title, String goal, String assessment,
                        LocalDate startDate, LocalDate endDate, boolean publish, List<ExerciseInput> exercises, Long version) {
            this(classId, memberId, title, goal, assessment, startDate, endDate, publish, exercises, version, "INDIVIDUAL");
        }
        @AssertTrue(message = "Kế hoạch cá nhân cần học viên; kế hoạch cả lớp không chọn học viên riêng")
        public boolean isAudienceValid() { return "CLASS".equals(scope) ? memberId == null : memberId != null; }
    }

    public record ExerciseInput(
            @Positive Integer id,
            LocalDate trainingDate,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String instructions,
            @NotBlank @Size(max = 255) String target) { }

    public record ReviewPlan(
            @NotBlank @Pattern(regexp = "ASSIGNED|COMPLETED|CANCELLED") String status,
            @Size(max = 2000) String coachFeedback,
            @NotNull @PositiveOrZero Long version) { }

    public record ExerciseResult(boolean completed, @Size(max = 2000) String memberResult,
                                 @NotNull @PositiveOrZero Long version) { }

    public record ExerciseView(Integer id, LocalDate trainingDate, String name, String instructions,
                               String target, boolean completed, String memberResult, boolean hasResults) { }

    public record MemberOption(Integer memberId, String memberName) { }
    public record RecipientView(Integer memberId, String memberName, long completedExercises, int totalExercises,
                                boolean unread, boolean enrolled, String coachFeedback, List<ExerciseView> exercises) { }
    public record VersionInput(@NotNull @PositiveOrZero Long version) { }
    public record RecipientFeedback(@Size(max = 2000) String coachFeedback, @NotNull @PositiveOrZero Long version) { }

    public record PlanView(
            Integer id, Integer classId, String className, Integer memberId, String memberName,
            String coachName, String createdByName, String title, String goal, String assessment,
            LocalDate startDate, LocalDate endDate, String status, String coachFeedback,
            int publishedRevision, boolean unread, LocalDateTime publishedAt, LocalDateTime updatedAt,
            long version, boolean editable, boolean canRecord, int totalExercises, long completedExercises,
            List<ExerciseView> exercises, String scope, int recipientCount,
            String personalFeedback, List<RecipientView> recipients, List<MemberOption> eligibleNewMembers) { }

    public record Result<T>(boolean success, String message, T data) {
        public static <T> Result<T> ok(T data) { return new Result<>(true, "Thành công", data); }
    }
}
