package com.swp391.beswp.dto;

import com.swp391.beswp.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class MemberResponse {
    private boolean success;
    private String message;
    private MemberData data;

    public static MemberResponse success(User user) {
        return new MemberResponse(true, "Lay thong tin hoc vien thanh cong", MemberData.fromEntity(user));
    }

    @Getter
    @AllArgsConstructor
    public static class MemberData {
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

        public static MemberData fromEntity(User user) {
            return new MemberData(user.getId(), user.getFullName(), user.getUsername(), user.getEmail(),
                    user.getPhone(), user.getDob(), user.getGender(), user.getAddress(), user.getAvatarUrl(),
                    user.getRole().getRoleName(), user.getStatus());
        }
    }
}
