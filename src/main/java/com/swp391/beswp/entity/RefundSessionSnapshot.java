package com.swp391.beswp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Embeddable @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RefundSessionSnapshot {
    @Column(name = "Session_ID", nullable = false)
    private Integer sessionId;
    @Column(name = "Session_Date", nullable = false)
    private LocalDate date;
    @Column(name = "Start_Time", nullable = false)
    private LocalTime startTime;
    @Column(name = "Refundable", nullable = false)
    private boolean refundable;
}
