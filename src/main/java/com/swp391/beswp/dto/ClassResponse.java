package com.swp391.beswp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.swp391.beswp.entity.ClassEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClassResponse {

    private boolean success;
    private String message;
    private ClassData data;

    public static ClassResponse success(String message, ClassEntity classEntity) {
        return new ClassResponse(true, message, ClassData.fromEntity(classEntity));
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ClassData {
        private Integer id;
        private String code;
        private String name;

        private SubjectInfo subject;
        private CoachInfo coach;
        private RoomInfo room;

        private List<String> daysOfWeek;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime startTime;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime endTime;

        private String timeSlot;
        private Integer maxCapacity;
        private BigDecimal price;
        private String status;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        public static ClassData fromEntity(ClassEntity entity) {
            if (entity == null) {
                return null;
            }

            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            String timeSlot = "";
            if (entity.getStartTime() != null && entity.getEndTime() != null) {
                timeSlot = entity.getStartTime().format(timeFormatter) + " - " + entity.getEndTime().format(timeFormatter);
            }

            List<String> daysList = Collections.emptyList();
            if (entity.getDaysOfWeek() != null && !entity.getDaysOfWeek().isBlank()) {
                daysList = Arrays.stream(entity.getDaysOfWeek().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }

            return ClassData.builder()
                    .id(entity.getId())
                    .code(String.format("CLS-%03d", entity.getId()))
                    .name(entity.getName())
                    .subject(entity.getSubject() != null ? new SubjectInfo(
                            entity.getSubject().getId(),
                            String.format("SUB-%03d", entity.getSubject().getId()),
                            entity.getSubject().getName(),
                            entity.getSubject().getCategory()
                    ) : null)
                    .coach(entity.getCoach() != null ? new CoachInfo(
                            entity.getCoach().getId(),
                            entity.getCoach().getFullName(),
                            entity.getCoach().getUsername(),
                            entity.getCoach().getAvatarUrl()
                    ) : null)
                    .room(entity.getRoom() != null ? new RoomInfo(
                            entity.getRoom().getId(),
                            entity.getRoom().getName(),
                            entity.getRoom().getMaxCapacity()
                    ) : null)
                    .daysOfWeek(daysList)
                    .startTime(entity.getStartTime())
                    .endTime(entity.getEndTime())
                    .timeSlot(timeSlot)
                    .maxCapacity(entity.getMaxCapacity())
                    .price(entity.getPrice())
                    .status(entity.getStatus())
                    .startDate(entity.getDate())
                    .build();
        }
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubjectInfo {
        private Integer id;
        private String code;
        private String name;
        private String category;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CoachInfo {
        private Integer id;
        private String fullName;
        private String username;
        private String avatarUrl;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoomInfo {
        private Integer id;
        private String name;
        private Integer maxCapacity;
    }
}
