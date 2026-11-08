package com.swp391.beswp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "Refund_Request", schema = "dbo")
@Getter @Setter
public class RefundRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "Refund_ID")
    private Integer id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Payment_ID", nullable = false, unique = true)
    private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Requested_By", nullable = false)
    private User requestedBy;
    @Column(name = "Item_Name", nullable = false, length = 150)
    private String itemName;
    @Column(name = "Paid_Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;
    @Column(name = "Estimated_Amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedAmount;
    @Column(name = "Approved_Amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;
    @Column(name = "Total_Sessions", nullable = false)
    private int totalSessions;
    @Column(name = "Cancelled_Sessions", nullable = false)
    private int cancelledSessions;
    @Column(name = "Status", nullable = false, length = 20)
    private String status = "PENDING";
    @Column(name = "Reason", nullable = false, length = 2000)
    private String reason;
    @Column(name = "Refund_Policy", length = 40)
    private String refundPolicy;
    @Column(name = "Review_Note", length = 2000)
    private String reviewNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Reviewed_By")
    private User reviewedBy;
    @Column(name = "Reviewed_At")
    private LocalDateTime reviewedAt;
    @Column(name = "Payout_Method", length = 20)
    private String payoutMethod;
    @Column(name = "Pickup_Code", length = 22)
    private String pickupCode;
    @Column(name = "Bank_Name", length = 100)
    private String bankName;
    @Column(name = "Account_Number", length = 40)
    private String accountNumber;
    @Column(name = "Account_Holder", length = 100)
    private String accountHolder;
    @Column(name = "Payout_Reference", length = 150)
    private String payoutReference;
    @Column(name = "Payout_Note", length = 1000)
    private String payoutNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "Paid_By")
    private User paidBy;
    @Column(name = "Paid_At")
    private LocalDateTime paidAt;
    @Column(name = "Created_At", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "Updated_At", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "Notification_Revision", nullable = false)
    private int notificationRevision = 1;
    @Column(name = "Read_Revision", nullable = false)
    private int readRevision;
    @Version @Column(name = "Version", nullable = false)
    private long version;
    @ElementCollection
    @CollectionTable(name = "Refund_Session", schema = "dbo", joinColumns = @JoinColumn(name = "Refund_ID"))
    @OrderColumn(name = "Position")
    private List<RefundSessionSnapshot> sessions = new ArrayList<>();
}
