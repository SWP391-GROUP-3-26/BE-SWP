package com.swp391.beswp.dto;

import com.swp391.beswp.entity.Subject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubjectListResponse {

    private boolean success;
    private String message;
    private int total;
    private List<SubjectResponse.SubjectData> data;

    public static SubjectListResponse success(List<Subject> subjects) {
        List<SubjectResponse.SubjectData> list = subjects.stream()
                .map(SubjectResponse.SubjectData::fromEntity)
                .toList();
        return new SubjectListResponse(true, "Lấy danh sách môn học thành công", list.size(), list);
    }
}
