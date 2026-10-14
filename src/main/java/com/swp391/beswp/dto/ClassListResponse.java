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
        return success(classes, java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    public static ClassListResponse success(List<ClassEntity> classes, java.time.LocalDate today) {
        List<ClassResponse.ClassData> list = classes.stream()
                .map(c -> ClassResponse.ClassData.fromEntity(c, today))
                .toList();
        return new ClassListResponse(true, "Lấy danh sách lớp học thành công", list.size(), list);
    }
}
