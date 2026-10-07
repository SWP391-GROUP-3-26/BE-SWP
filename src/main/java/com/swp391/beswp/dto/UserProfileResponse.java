package com.swp391.beswp.dto;

import com.swp391.beswp.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class UserProfileResponse {

    private boolean success;
    private String message;
    private UserProfileData data;

    public static UserProfileResponse fromEntity(User user, String message) {
        return new UserProfileResponse(true, message, UserProfileData.fromEntity(user));
    }

    @Getter
    @AllArgsConstructor
    public static class UserProfileData {

        private Integer userId;
        private String fullName;
        private String username;
        private String email;
        private String phone;
        private LocalDate dob;
        private String gender;
        private String address;
        private String avatarUrl;
        private String role;
        private String status;

        private static UserProfileData fromEntity(User user) {
            return new UserProfileData(
                    user.getId(),
                    user.getFullName(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getDob(),
                    user.getGender(),
                    user.getAddress(),
                    user.getAvatarUrl(),
                    user.getRole() == null ? null : user.getRole().getRoleName(),
                    user.getStatus()
            );
        }
    }
}
