package com.swp391.beswp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClassFormOptionsResponse {

    private boolean success;
    private String message;
    private OptionData data;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class OptionData {
        private List<SubjectOption> subjects;
        private List<CoachOption> coaches;
        private List<RoomOption> rooms;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SubjectOption {
        private Integer id;
        private String code;
        private String name;
        private String category;
        private String displayText; // "Hatha Yoga Định Tuyến (SUB-003)"
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CoachOption {
        private Integer id;
        private String fullName;
        private String username;
        private String phone;
        private String displayText; // "HLV Master Đặng Thu Trang"
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class RoomOption {
        private Integer id;
        private String name;
        private Integer maxCapacity;
        private String displayText; // "Studio Lotus 01 (Tầng 2) - Tối đa 20 người"
    }
}
