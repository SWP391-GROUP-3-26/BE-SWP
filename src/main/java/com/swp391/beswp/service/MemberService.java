package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberListResponse;
import com.swp391.beswp.dto.MemberResponse;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final UserRepository userRepository;

    public MemberListResponse searchMembers(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0; size must be between 1 and 100");
        }
        var pageable = PageRequest.of(page, size, Sort.by("id"));
        String normalized = keyword == null ? "" : keyword.trim();
        Page<User> members = normalized.isEmpty() ? Page.empty(pageable)
                : userRepository.searchMembers(normalized, pageable);
        return MemberListResponse.success(members);
    }

    public MemberResponse getMemberById(Integer id) {
        User member = userRepository.findByIdAndRoleRoleNameIgnoreCase(id, "Member")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        return MemberResponse.success(member);
    }
}
