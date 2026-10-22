package com.swp391.beswp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO dùng để cập nhật thông tin gói dịch vụ.
 * Tất cả các trường đều là tuỳ chọn (null = không thay đổi).
 */
@Getter
@Setter
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

    @DecimalMin(value = "0.0", message = "Chiết khấu không được âm")
    private BigDecimal discount;

    private String termConditions;

    /** null = giữ nguyên; [] = bỏ toàn bộ Included Sports. */
    private List<Integer> includedSportIds;

    /** null = giữ nguyên; [] = bỏ toàn bộ Choice Groups. */
    @jakarta.validation.Valid
    private List<CreatePackageRequest.ChoiceGroupRequest> choiceGroups;
}
