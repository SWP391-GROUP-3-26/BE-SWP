package com.swp391.beswp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "Payment", schema = "dbo")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Payment_ID")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Subscription_ID")
    private MemberSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Receptionist_ID")
    private User receptionist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "Booking_ID")
    private ClassBooking booking;

    @Column(name = "Payment_Type", nullable = false, length = 30)
    private String paymentType;

    @Column(name = "Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "Method", nullable = false, length = 30)
    private String method;

    @Column(name = "Payment_Date", nullable = false)
    @Builder.Default
    private LocalDateTime paymentDate = LocalDateTime.now();

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private String status = "Pending";

    @Column(name = "Discount_Amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "Expires_At")
    private LocalDateTime expiresAt;

    @Column(name = "Vnp_Transaction_No", length = 15)
    private String vnpTransactionNo;

    @Column(name = "Vnp_Response_Code", length = 2)
    private String vnpResponseCode;

    @Column(name = "Vnp_Transaction_Status", length = 2)
    private String vnpTransactionStatus;

    @Column(name = "Vnp_Pay_Date", length = 14)
    private String vnpPayDate;

    @Column(name = "Requires_Review", nullable = false)
    @Builder.Default
    private boolean requiresReview = false;
}
