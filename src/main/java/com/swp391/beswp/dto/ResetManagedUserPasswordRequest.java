package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetManagedUserPasswordRequest {

    @NotBlank(message = "Password is required")
    @Size(max = 72, message = "Password must not exceed 72 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    @Size(max = 72, message = "Confirm password must not exceed 72 characters")
    private String confirmPassword;
}
