package com.swp391.beswp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** One locked row per normalized email: send limits are shared across all OTP purposes. */
@Entity
@Table(name = "Email_Otp_Challenge", schema = "dbo")
@Getter @Setter
public class EmailOtpChallenge {
    @Id @Column(name = "Email", length = 100) private String email;
    @Column(name = "Purpose", length = 20) private String purpose;
    @Column(name = "Context_Hash", length = 64) private String contextHash;
    @Column(name = "Context_Expires_At") private Instant contextExpiresAt;
    @Column(name = "User_ID") private Integer userId;
    @Column(name = "Session_Version") private Long sessionVersion;
    @Column(name = "Original_Email", length = 100) private String originalEmail;
    @Column(name = "Otp_Hash", length = 255) private String otpHash;
    @Column(name = "Otp_Expires_At") private Instant otpExpiresAt;
    @Column(name = "Failed_Attempts", nullable = false) private int failedAttempts;
    @Column(name = "Last_Sent_At") private Instant lastSentAt;
    @Column(name = "Window_Started_At") private Instant windowStartedAt;
    @Column(name = "Send_Count", nullable = false) private int sendCount;
    @Column(name = "Send_History", length = 1000) private String sendHistory;
    @Column(name = "Pending_Username", length = 50) private String pendingUsername;
    @Column(name = "Pending_FullName", length = 100) private String pendingFullName;
    @Column(name = "Pending_Phone", length = 15) private String pendingPhone;
    @Column(name = "Pending_Password_Hash", length = 255) private String pendingPasswordHash;
    @Column(name = "Reset_Hash", length = 64) private String resetHash;
    @Column(name = "Reset_Expires_At") private Instant resetExpiresAt;
}
