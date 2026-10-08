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

    @NotBlank(message = "Tên lớp học không được để trống")
    @Size(max = 100, message = "Tên lớp học không được vượt quá 100 ký tự")
    private String name;

    @NotNull(message = "Vui lòng chọn môn học cho lớp học")
    private Integer subjectId;

    @NotNull(message = "Vui lòng chọn Huấn Luyện Viên (HLV/PT) phụ trách")
    private Integer coachId;

    @NotNull(message = "Vui lòng chọn phòng tập chỉ định")
    private Integer roomId;

    @NotEmpty(message = "Vui lòng chọn ít nhất một ngày học trong tuần")
    private List<String> daysOfWeek;

    @NotNull(message = "Giờ bắt đầu buổi học không được để trống")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc buổi học không được để trống")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    @NotNull(message = "Sức chứa tối đa không được để trống")
    @Min(value = 1, message = "Sức chứa tối đa phải lớn hơn 0")
    private Integer maxCapacity;

    @NotNull(message = "Học phí không được để trống")
    @DecimalMin(value = "0.0", message = "Học phí không được âm")
    private BigDecimal price;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
}
