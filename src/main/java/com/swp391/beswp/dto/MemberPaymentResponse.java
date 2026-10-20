package com.swp391.beswp.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
public class MemberPaymentResponse {
    private boolean success;
    private Integer paymentId;
    private Integer memberId;
    private String memberName;
    private String itemName;
    private Integer bookingId;
    private String paymentStatus;
    private String bookingStatus;
    private BigDecimal paymentAmount;
    private OffsetDateTime expiresAt;
    private boolean requiresReview;
    private SepayQrPayment qrPayment;
}
