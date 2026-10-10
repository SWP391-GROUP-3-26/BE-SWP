package com.swp391.beswp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePackageRequest {

    @NotBlank(message = "Tên gói dịch vụ không được để trống")
    @Size(max = 100, message = "Tên gói dịch vụ không được vượt quá 100 ký tự")
    private String name;

    /** Mô tả chi tiết & đặc quyền của gói */
    private String description;

    /**
     * Thời gian hiệu lực tính theo số tháng.
     * VD: 1 → 30 ngày, 3 → 90 ngày, 6 → 180 ngày, 12 → 365 ngày, 24 → 730 ngày.
     * BE sẽ quy đổi sang số ngày trước khi lưu.
     */
    @NotNull(message = "Thời gian hiệu lực không được để trống")
    @Min(value = 1, message = "Thời gian hiệu lực tối thiểu là 1 tháng")
    private Integer durationMonths;

    @NotNull(message = "Giá niêm yết không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá niêm yết phải lớn hơn 0")
    private BigDecimal price;

    /**
     * Trạng thái phát hành: 'Active' (Đang mở bán) hoặc 'Inactive' (Tạm ngưng cung cấp).
     * Mặc định là 'Active' nếu không truyền.
     */
    @Pattern(
            regexp = "^(Active|Inactive)$",
            message = "Trạng thái chỉ được là 'Active' (Đang mở bán) hoặc 'Inactive' (Tạm ngưng)"
    )
    private String status;

    /** Số buổi học bao gồm trong gói (tuỳ chọn, mặc định 0). */
    private Integer includedClasses;

    /** Đặc quyền thành viên (tuỳ chọn, bổ sung thêm ngoài description). */
    private String benefits;

    /** Điều khoản & điều kiện (tuỳ chọn). */
    private String termConditions;
}
