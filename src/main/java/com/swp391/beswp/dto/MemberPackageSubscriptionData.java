package com.swp391.beswp.dto;

import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberPackageSubscriptionData {

    private Integer subscriptionId;
    private Integer packageId;
    private String packageName;
    private BigDecimal packagePrice;
    private Integer durationDays;
    private Integer totalFreezeMonths;
    private Integer usedFreezeMonths;
    private Integer remainingFreezeMonths;
    private LocalDateTime freezeStartedAt;
    private LocalDateTime freezeEndsAt;
    private String freezeTerms;
    private LocalDate startDate;
    private LocalDate endDate;
    private String subscriptionStatus;
    private Integer paymentId;
    private String paymentMethod;
    private BigDecimal paymentAmount;
    private BigDecimal discountAmount;
    private String paymentStatus;
    @lombok.Setter
    private String paymentUrl;
    private LocalDateTime paymentDate;

    public static MemberPackageSubscriptionData fromEntities(MemberSubscription subscription, Payment payment) {
        return MemberPackageSubscriptionData.builder()
                .subscriptionId(subscription.getId())
                .packageId(subscription.getMembershipPackage().getId())
                .packageName(subscription.getPackageNameAtPurchase())
                .packagePrice(subscription.getPriceAtPurchase())
                .durationDays(subscription.getDurationAtPurchase())
                .totalFreezeMonths(subscription.getFreezeMonthsAtPurchase())
                .usedFreezeMonths(subscription.getFreezeMonthsUsed())
                .remainingFreezeMonths(Math.max(0, subscription.getFreezeMonthsAtPurchase() - subscription.getFreezeMonthsUsed()))
                .freezeStartedAt(subscription.getFreezeStartedAt()).freezeEndsAt(subscription.getFreezeEndsAt())
                .freezeTerms(subscription.getFreezeMonthsAtPurchase() > 0 ? com.swp391.beswp.service.MembershipFreezePolicy.TERMS : null)
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .subscriptionStatus(subscription.getStatus())
                .paymentId(payment == null ? null : payment.getId())
                .paymentMethod(payment == null ? null : payment.getMethod())
                .paymentAmount(payment == null ? null : payment.getAmount())
                .discountAmount(payment == null ? null : payment.getDiscountAmount())
                .paymentStatus(payment == null ? null : payment.getStatus())
                .paymentDate(payment == null ? null : payment.getPaymentDate())
                .build();
    }
}
