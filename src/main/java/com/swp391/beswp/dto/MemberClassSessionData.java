package com.swp391.beswp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

public record MemberClassSessionData(
        Integer sessionId,
        LocalDate sessionDate,
        @JsonFormat(pattern = "HH:mm") LocalTime sessionStartTime,
        @JsonFormat(pattern = "HH:mm") LocalTime sessionEndTime,
        String sessionStatus,
        String rawStatus
) {}
