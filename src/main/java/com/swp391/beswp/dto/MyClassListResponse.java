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
public class MyClassListResponse {

    private boolean success;
    private String message;
    private Counts counts;
    private List<MyClassItemResponse> classes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Counts {
        private long all;
        private long ongoing;
        private long completed;
        private long cancelled;
    }
}
