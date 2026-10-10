package com.swp391.beswp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO dùng để cập nhật thông tin gói dịch vụ.
 * Tất cả các trường đều là tuỳ chọn (null = không thay đổi).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePackageRequest {

    @Size(max = 100, message = "Tên gói dịch vụ không được vượt quá 100 ký tự")
    private String name;

    private String description;

    @Min(value = 1, message = "Thời gian hiệu lực tối thiểu là 1 tháng")
    private Integer durationMonths;

    @DecimalMin(value = "0.0", inclusive = false, message = "Giá niêm yết phải lớn hơn 0")
    private BigDecimal price;

    @Pattern(
            regexp = "^(Active|Inactive)$",
            message = "Trạng thái chỉ được là 'Active' hoặc 'Inactive'"
    )
    private String status;

    private Integer includedClasses;
    private String benefits;
    private String termConditions;
}
