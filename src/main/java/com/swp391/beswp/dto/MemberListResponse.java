package com.swp391.beswp.dto;

import com.swp391.beswp.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;
import java.util.List;

@Getter
@AllArgsConstructor
public class MemberListResponse {
    private boolean success;
    private String message;
    private long total;
    private int page;
    private int size;
    private List<MemberResponse.MemberData> data;

    public static MemberListResponse success(Page<User> members) {
        return new MemberListResponse(true, "Tim hoc vien thanh cong", members.getTotalElements(),
                members.getNumber(), members.getSize(), members.getContent().stream()
                .map(MemberResponse.MemberData::fromEntity).toList());
    }
}
