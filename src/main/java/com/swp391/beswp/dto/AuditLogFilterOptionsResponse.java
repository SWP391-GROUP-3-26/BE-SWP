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
public class AuditLogFilterOptionsResponse {

    private List<String> categories;
    private List<ActionOption> actions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActionOption {
        private String key;
        private String label;
        private String color;
    }
}
