package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MemberPackageRegistrationRequest {

    @NotNull(message = "packageId không được để trống")
    @Positive(message = "packageId phải lớn hơn 0")
    private Integer packageId;

    @NotBlank(message = "paymentMethod không được để trống")
    @Size(max = 30, message = "paymentMethod không được vượt quá 30 ký tự")
    private String paymentMethod;

    /** Lựa chọn Sport cho từng Choice Group của Package. */
    private List<ChoiceGroupSelectionRequest> choiceGroups;

    /** IMMEDIATE (mặc định) hoặc AFTER_CURRENT khi gia hạn. */
    private String renewalStart;

    @Getter
    @Setter
    public static class ChoiceGroupSelectionRequest {
        @NotNull(message = "choiceGroupId không được để trống")
        @Positive(message = "choiceGroupId phải lớn hơn 0")
        private Integer choiceGroupId;

        @NotNull(message = "sportIds không được để trống")
        private List<@NotNull @Positive Integer> sportIds;
    }
}
