package com.swp391.beswp.service;

import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.repository.MemberPackageSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Chỉ system được phép chuyển trạng thái theo thời hạn membership. */
@Service
@RequiredArgsConstructor
public class MembershipLifecycleService {

    private final MemberPackageSubscriptionRepository subscriptions;
    private final com.swp391.beswp.repository.UserRepository users;
    private final jakarta.persistence.EntityManager entityManager;
    private final java.time.Clock paymentClock;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void updateStatuses() {
        LocalDate today = LocalDate.now(paymentClock);
        List<MemberSubscription> expired = subscriptions.findExpiredActiveSubscriptions(today);
        List<MemberSubscription> due = subscriptions.findDuePendingSubscriptions(today);
        var memberIds = java.util.stream.Stream.concat(expired.stream(), due.stream())
                .map(s -> s.getUser().getId()).distinct().sorted().toList();
        // Share the purchase/callback lock; lock members in one order across scheduler instances.
        for (Integer memberId : memberIds) {
            users.findByIdForUpdate(memberId).orElseThrow();
            for (MemberSubscription subscription : expired) {
                if (!memberId.equals(subscription.getUser().getId())) continue;
                entityManager.refresh(subscription);
                if ("Active".equals(subscription.getStatus()) && subscription.getEndDate().isBefore(today)) subscription.setStatus("Expired");
            }
            for (MemberSubscription pending : due) {
                if (!memberId.equals(pending.getUser().getId())) continue;
                entityManager.refresh(pending);
                if (!"Pending".equals(pending.getStatus())) continue;
                if (pending.getEndDate().isBefore(today)) pending.setStatus("Expired");
                else if (!subscriptions.hasActiveSubscription(memberId, today)) pending.setStatus("Active");
            }
        }
    }
}
