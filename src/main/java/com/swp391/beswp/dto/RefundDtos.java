package com.swp391.beswp.dto;

import com.swp391.beswp.service.ApiMessages;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class RefundDtos {
    private RefundDtos() { }
    public record Result<T>(boolean success, String message, T data) {
        public static <T> Result<T> ok(T data) { return new Result<>(true, ApiMessages.text("api.success"), data); }
    }
    public record SessionView(Integer sessionId, LocalDate date, LocalTime startTime, boolean refundable) { }
    public record CancellationPreview(Integer bookingId, int totalSessions, int cancelSessions, BigDecimal paidAmount,
                                      BigDecimal estimatedRefund, boolean usesPackage, List<SessionView> sessions,
                                      int packageSessionsReturned) { }
    public record CancelConfirmation(
            @NotNull(message = "{class.cancel.refund.required}")
            @PositiveOrZero(message = "{class.cancel.refund.minimum}") BigDecimal estimatedRefund,
            @NotNull(message = "{class.cancel.sessions.required}")
            @Positive(message = "{class.cancel.sessions.minimum}") Integer cancelSessions) { }
    public record Review(@NotBlank @Pattern(regexp = "APPROVED|REJECTED") String status,
                         @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal approvedAmount,
                         @Size(max = 2000) String note, @NotNull @PositiveOrZero Long version) { }
    public record Recipient(@NotBlank @Pattern(regexp = "CASH", message = "Chỉ hỗ trợ nhận tiền mặt tại quầy") String method,
                            @NotNull @PositiveOrZero Long version) { }
    public record Payout(@NotBlank @Size(max = 64) String pickupCode, @Size(max = 1000) String note,
                         @NotNull @PositiveOrZero Long version) { }
    public record RefundView(Integer id, Integer paymentId, Integer memberId, String memberName, String itemName,
                             String status, BigDecimal paidAmount, BigDecimal estimatedAmount, BigDecimal approvedAmount,
                             int totalSessions, int cancelledSessions, String reason, String reviewNote,
                             String reviewedBy, LocalDateTime reviewedAt, String payoutMethod, String bankName,
                             String accountNumber, String accountHolder, String payoutReference, String payoutNote,
                             String paidBy, LocalDateTime paidAt, LocalDateTime createdAt, LocalDateTime updatedAt,
                             int notificationRevision, boolean unread, long version, List<SessionView> sessions,
                             String pickupCode, boolean pickupReady) { }
    public record LegacyPayment(Integer paymentId, String memberName, String itemName, BigDecimal amount) { }
    public record StaffQueue(List<RefundView> refunds, List<LegacyPayment> legacyPayments) { }
    public record PaymentView(Integer id, String itemName, String paymentType, BigDecimal amount, BigDecimal discountAmount,
                              BigDecimal originalAmount, String method, String status, LocalDateTime createdAt,
                              LocalDateTime paidAt, boolean requiresReview, String gatewayReference,
                              BigDecimal refundedAmount, BigDecimal retainedAmount, RefundView refund) { }
    public record PaymentPage(List<PaymentView> items, int page, int size, long total) { }
}
