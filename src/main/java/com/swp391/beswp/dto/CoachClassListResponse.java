package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachClassListResponse {

    private boolean success;
    private String message;
    private Integer coachId;
    private String coachName;
    private Counts counts;
    private List<CoachClassItemResponse> classes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Counts {
        private long totalClasses;
        private long activeClasses;
        private long completedClasses;
        private long cancelledClasses;
        private long totalStudents;
    }
}
