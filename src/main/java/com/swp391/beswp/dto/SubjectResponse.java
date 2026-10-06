package com.swp391.beswp.dto;

import com.swp391.beswp.entity.Subject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubjectResponse {

    private boolean success;
    private String message;
    private SubjectData data;

    public static SubjectResponse success(String message, Subject subject) {
        return new SubjectResponse(true, message, SubjectData.fromEntity(subject));
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SubjectData {
        private Integer id;
        private String code;
        private String name;
        private String category;
        private String description;

        public static SubjectData fromEntity(Subject subject) {
            if (subject == null) {
                return null;
            }
            return SubjectData.builder()
                    .id(subject.getId())
                    .code(String.format("SUB-%03d", subject.getId()))
                    .name(subject.getName())
                    .category(subject.getCategory())
                    .description(subject.getDescription())
                    .build();
        }
    }
}
