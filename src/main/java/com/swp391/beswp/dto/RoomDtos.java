package com.swp391.beswp.dto;

import com.swp391.beswp.entity.Room;
import com.swp391.beswp.entity.RoomType;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class RoomDtos {
    private RoomDtos() { }

    public record SaveRequest(@NotBlank @Size(max = 50) String name, @NotNull RoomType type,
            @NotNull @Min(1) Integer maxCapacity, @NotBlank @Size(max = 150) String location,
            @NotBlank @Pattern(regexp = "Active|Maintenance|Inactive") String status) { }
    public record StatusRequest(@NotBlank @Pattern(regexp = "Active|Maintenance|Inactive") String status) { }
    public record TypeOption(RoomType code, String label) { }
    public record StatusOption(String code, String label) { }
    public record Options(List<TypeOption> types, List<StatusOption> statuses) { }
    public record AssignedClass(Integer classId, String name, String subjectName, Integer maxCapacity,
            LocalDate startDate, LocalDate endDate, String status) { }
    public record Data(Integer id, String code, String name, RoomType type, String typeLabel, Integer maxCapacity,
            String location, String status, String statusLabel, List<AssignedClass> upcomingClasses) {
        public static Data from(Room r, List<AssignedClass> classes) {
            return new Data(r.getId(), String.format("ROOM-%03d", r.getId()), r.getName(), r.getType(),
                    r.getType() == null ? null : r.getType().getLabel(), r.getMaxCapacity(), r.getLocation(), r.getStatus(),
                    switch (r.getStatus()) { case "Active" -> "Hoạt động"; case "Maintenance" -> "Bảo trì";
                        default -> "Ngừng sử dụng"; }, classes);
        }
    }
    public record Response<T>(boolean success, String message, T data) { }
}
