package com.swp391.beswp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class VoucherDtos {
    private VoucherDtos() {
    }

    public record VoucherOption(
            Integer voucherId,
            String code,
            String discountType,
            BigDecimal discountValue,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    public record MemberPackageCheckoutRequest(
            @NotNull @Valid MemberPackageRegistrationRequest purchase,
            @NotBlank String voucherCode
    ) {
    }

    public record MemberClassCheckoutRequest(
            @NotNull @Valid MemberClassBookingRequest purchase,
            @NotBlank String voucherCode
    ) {
    }

    public record ReceptionistPackageCheckoutRequest(
            @NotNull @Valid ReceptionistPackageRegistrationRequest purchase,
            @NotBlank String voucherCode
    ) {
    }

    public record ReceptionistClassCheckoutRequest(
            @NotNull @Valid ReceptionistClassRegistrationRequest purchase,
            @NotBlank String voucherCode
    ) {
    }

    public record VoucherCheckoutResponse(
            boolean success,
            String message,
            Integer memberId,
            String targetType,
            Integer targetId,
            Integer subscriptionId,
            Integer bookingId,
            Integer paymentId,
            String paymentStatus,
            String paymentMethod,
            String voucherCode,
            String discountType,
            BigDecimal discountValue,
            BigDecimal amountBeforeVoucher,
            BigDecimal voucherDiscountAmount,
            BigDecimal totalDiscountAmount,
            BigDecimal paymentAmount,
            String paymentUrl,
            SepayQrPayment qrPayment
    ) {
    }

    public static VoucherCheckoutResponse success(
            String message,
            Integer memberId,
            String targetType,
            Integer targetId,
            Integer subscriptionId,
            Integer bookingId,
            Integer paymentId,
            String paymentStatus,
            String paymentMethod,
            VoucherOption voucher,
            BigDecimal amountBeforeVoucher,
            BigDecimal voucherDiscountAmount,
            BigDecimal totalDiscountAmount,
            BigDecimal paymentAmount,
            String paymentUrl,
            SepayQrPayment qrPayment
    ) {
        return new VoucherCheckoutResponse(true, message, memberId, targetType, targetId, subscriptionId,
                bookingId, paymentId, paymentStatus, paymentMethod, voucher.code(), voucher.discountType(),
                voucher.discountValue(), amountBeforeVoucher, voucherDiscountAmount, totalDiscountAmount,
                paymentAmount, paymentUrl, qrPayment);
    }
}
