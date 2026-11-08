package com.swp391.beswp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateClassRequest {

    @NotBlank(message = "{class.name.required}")
    @Size(max = 100, message = "{class.name.length}")
    private String name;

    @NotNull(message = "{class.subject.required}")
    private Integer subjectId;

    @NotNull(message = "{class.coach.required}")
    private Integer coachId;

    @NotNull(message = "{class.room.required}")
    private Integer roomId;

    @NotEmpty(message = "{class.days.required}")
    private List<String> daysOfWeek;

    @NotNull(message = "{class.slot.required}")
    private Integer slotId;

    @NotNull(message = "{class.startTime.required}")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "{class.endTime.required}")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @NotNull(message = "{class.capacity.required}")
    @Min(value = 1, message = "{class.capacity.minimum}")
    private Integer maxCapacity;

    @NotNull(message = "{class.price.required}")
    @DecimalMin(value = "0.0", message = "{class.price.minimum}")
    private BigDecimal price;

    private String status;

    @NotNull(message = "{class.startDate.required}")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "{class.duration.required}")
    @Min(value = 1, message = "{class.duration.minimum}")
    private Integer durationWeeks;

    private Boolean hasPt;
}
