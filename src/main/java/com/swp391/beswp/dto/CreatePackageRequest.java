package com.swp391.beswp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request tạo Package mới.
 * Package gồm: thông tin cơ bản + Included Sports + Choice Groups.
 */
@Getter
@Setter
public class CreatePackageRequest {

    @NotBlank(message = "Tên gói dịch vụ không được để trống")
    @Size(max = 100, message = "Tên gói dịch vụ không được vượt quá 100 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Thời gian hiệu lực không được để trống")
    @Min(value = 1, message = "Thời gian hiệu lực tối thiểu là 1 tháng")
    private Integer durationMonths;

    @NotNull(message = "Giá niêm yết không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá niêm yết phải lớn hơn 0")
    private BigDecimal price;

    /** Chiết khấu phần trăm (0–100). Mặc định 0 nếu không truyền. */
    @DecimalMin(value = "0.0", message = "Chiết khấu không được âm")
    private BigDecimal discount;

    /** Điều khoản & điều kiện. */
    private String termConditions;

    @Pattern(
            regexp = "^(Active|Inactive)$",
            message = "Trạng thái chỉ được là 'Active' (Đang mở bán) hoặc 'Inactive' (Tạm ngưng)"
    )
    private String status;

    /**
     * Danh sách Subject ID là Included Sports của Package.
     * Có thể rỗng hoặc null → Package không có Included Sport.
     */
    private List<Integer> includedSportIds;

    /**
     * Danh sách Choice Groups của Package.
     * Có thể rỗng hoặc null → Package không có Choice Group.
     */
    @Valid
    private List<ChoiceGroupRequest> choiceGroups;

    @Getter
    @Setter
    public static class ChoiceGroupRequest {

        @NotBlank(message = "Tên Choice Group không được để trống")
        @Size(max = 100)
        private String groupName;

        private String description;

        @NotNull(message = "requiredSelection không được để trống")
        @Min(value = 1, message = "requiredSelection phải >= 1")
        private Integer requiredSelection;

        /** Thứ tự hiển thị. Mặc định 0. */
        private Integer sortOrder;

        /**
         * Danh sách Subject ID có thể chọn trong Group này.
         * Phải có ít nhất requiredSelection phần tử.
         */
        @NotNull(message = "Danh sách Sport trong Choice Group không được null")
        @Size(min = 1, message = "Choice Group phải có ít nhất 1 Sport")
        private List<Integer> sportIds;
    }
}
