package com.swp391.beswp.service;

import com.swp391.beswp.dto.UpdateUserProfileRequest;
import com.swp391.beswp.dto.UserProfileResponse;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String authenticatedUserId) {
        User user = findAuthenticatedUser(authenticatedUserId);
        return UserProfileResponse.fromEntity(user, "Lấy hồ sơ thành công");
    }

    @Transactional
    public UserProfileResponse updateMyProfile(
            String authenticatedUserId,
            UpdateUserProfileRequest request
    ) {
        User user = findAuthenticatedUser(authenticatedUserId);

        String phone = normalizeOptional(request.getPhone());
        String currentPhone = normalizeOptional(user.getPhone());
        if (phone != null && !phone.equals(currentPhone) && userRepository.existsByPhone(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã được sử dụng");
        }

        user.setFullName(request.getFullName().trim());
        user.setPhone(phone);
        user.setDob(request.getDob());
        user.setGender(normalizeOptional(request.getGender()));
        user.setAddress(normalizeOptional(request.getAddress()));
        user.setAvatarUrl(normalizeOptional(request.getAvatarUrl()));

        User savedUser = userRepository.save(user);
        return UserProfileResponse.fromEntity(savedUser, "Cập nhật hồ sơ thành công");
    }

    private User findAuthenticatedUser(String authenticatedUserId) {
        final Integer userId;
        try {
            userId = Integer.valueOf(authenticatedUserId);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Danh tính đăng nhập không hợp lệ");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
