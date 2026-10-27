package com.swp391.beswp.service;

import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

/** Subscription callbacks use the same member lock as purchases, including cash purchases. */
@Service
@RequiredArgsConstructor
public class OnlineSubscriptionPaymentService {
    private final PaymentRepository payments;
    private final UserRepository users;
    private final MemberPackageSubscriptionRepository subscriptions;
    private final AuditLogService audit;
    private final Clock paymentClock;
    private final jakarta.persistence.EntityManager entityManager;

    public boolean hasPending(Integer memberId) {
        return payments.findAllBySubscription_User_IdAndStatus(memberId, "Pending").stream()
                .anyMatch(p -> p.getExpiresAt() != null && p.getExpiresAt().isAfter(now()));
    }

    public void expirePending(Integer memberId) {
        for (Payment p : payments.findAllBySubscription_User_IdAndStatus(memberId, "Pending")) {
            if (p.getExpiresAt() != null && !p.getExpiresAt().isAfter(now())) {
                p.setStatus("Failed");
                if ("Pending".equals(p.getSubscription().getStatus())) p.getSubscription().setStatus("Cancelled");
            }
        }
    }

    @Transactional
    public Payment lock(Integer paymentId) {
        Payment initial = payments.findById(paymentId).orElseThrow();
        users.findByIdForUpdate(initial.getSubscription().getUser().getId()).orElseThrow();
        // The initial lookup only obtains the owner; refresh after locking for duplicate callbacks.
        entityManager.refresh(initial);
        entityManager.refresh(initial.getSubscription());
        if (initial.getRenewalSubscription() != null) entityManager.refresh(initial.getRenewalSubscription());
        return initial;
    }

    public void confirm(Payment payment, boolean success) {
        if ("Paid".equals(payment.getStatus())) return;
        var subscription = payment.getSubscription();
        var member = subscription.getUser();
        var today = now().toLocalDate();
        var previous = payment.getRenewalSubscription();
        boolean membershipEligible;
        if (previous == null) {
            membershipEligible = !subscriptions.hasActiveSubscription(member.getId(), today);
        } else {
            var active = subscriptions.findCurrentActiveSubscriptions(member.getId(), today);
            membershipEligible = java.util.Objects.equals(previous.getUser().getId(), member.getId())
                    && ("Active".equals(previous.getStatus())
                        || ("Expired".equals(previous.getStatus()) && previous.getEndDate().isBefore(today)))
                    && active.stream().allMatch(s -> java.util.Objects.equals(s.getId(), previous.getId()))
                    && ("IMMEDIATE".equals(payment.getRenewalStart())
                        || ("AFTER_CURRENT".equals(payment.getRenewalStart())
                            && subscription.getStartDate().equals(previous.getEndDate().plusDays(1))));
        }
        boolean eligible = "Pending".equals(payment.getStatus()) && "Pending".equals(subscription.getStatus())
                && payment.getExpiresAt().isAfter(now()) && "Active".equalsIgnoreCase(member.getStatus())
                && membershipEligible && subscription.getEndDate().compareTo(today) >= 0;
        payment.setStatus(success ? "Paid" : "Failed");
        payment.setRequiresReview(success && !eligible);
        if (success && eligible) {
            if (previous != null && "IMMEDIATE".equals(payment.getRenewalStart())) previous.setStatus("Expired");
            subscription.setStatus(subscription.getStartDate().isAfter(today) ? "Pending" : "Active");
        }
        else if ("Pending".equals(subscription.getStatus())) subscription.setStatus("Cancelled");
        audit.recordLog(payment.getReceptionist() == null ? member : payment.getReceptionist(),
                "CONFIRM_PAYMENT", "Payment", "Thanh toán gói #" + payment.getId() + " cho hội viên #"
                        + member.getId() + ": " + payment.getStatus() + (payment.isRequiresReview() ? " - cần đối soát" : ""));
    }

    private LocalDateTime now() { return LocalDateTime.now(paymentClock).withNano(0); }
}
