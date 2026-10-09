package com.swp391.beswp.dto;

import com.swp391.beswp.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class UserManagementListResponse {

    private boolean success;
    private String message;
    private long total;
    private int page;
    private int size;
    private int totalPages;
    private List<UserManagementUserData> data;

    public static UserManagementListResponse fromPage(Page<User> users) {
        return new UserManagementListResponse(
                true,
                "Lấy danh sách người dùng thành công",
                users.getTotalElements(),
                users.getNumber(),
                users.getSize(),
                users.getTotalPages(),
                users.getContent().stream().map(UserManagementUserData::fromEntity).toList()
        );
    }
}
