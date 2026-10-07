package com.swp391.beswp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateUserProfileRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;

    @Size(max = 15, message = "Số điện thoại không được vượt quá 15 ký tự")
    private String phone;

    private LocalDate dob;

    @Size(max = 10, message = "Giới tính không được vượt quá 10 ký tự")
    @Pattern(regexp = "^(Male|Female|Other)?$", message = "Giới tính chỉ nhận Male, Female hoặc Other")
    private String gender;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Size(max = 255, message = "Avatar URL không được vượt quá 255 ký tự")
    private String avatarUrl;
}
