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
public class AuditLogListResponse {

    private boolean success;
    private String message;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private int numberOfElements;
    private AuditLogFilterOptionsResponse filterOptions;
    private List<AuditLogItemResponse> data;
}
