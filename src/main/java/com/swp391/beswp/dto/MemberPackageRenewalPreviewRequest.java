package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MemberPackageRenewalPreviewRequest {
    @NotNull(message = "packageId không được để trống")
    @Positive(message = "packageId phải lớn hơn 0")
    private Integer packageId;

    /** IMMEDIATE hoặc AFTER_CURRENT. */
    private String renewalStart;

    /** Sport chọn lại cho các Choice Group của Package mới. */
    private List<MemberPackageRegistrationRequest.ChoiceGroupSelectionRequest> choiceGroups;
}
