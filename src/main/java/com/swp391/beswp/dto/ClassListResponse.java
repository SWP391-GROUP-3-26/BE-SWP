package com.swp391.beswp.dto;

import com.swp391.beswp.entity.ClassEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClassListResponse {

    private boolean success;
    private String message;
    private int total;
    private List<ClassResponse.ClassData> data;

    public static ClassListResponse success(List<ClassEntity> classes) {
        List<ClassResponse.ClassData> list = classes.stream()
                .map(ClassResponse.ClassData::fromEntity)
                .toList();
        return new ClassListResponse(true, "Lấy danh sách lớp học thành công", list.size(), list);
    }
}
