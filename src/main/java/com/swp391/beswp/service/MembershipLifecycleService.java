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

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void updateStatuses() {
        LocalDate today = LocalDate.now();
        subscriptions.findExpiredActiveSubscriptions(today).forEach(s -> s.setStatus("Expired"));

        for (MemberSubscription pending : subscriptions.findDuePendingSubscriptions(today)) {
            List<MemberSubscription> active = subscriptions.findCurrentActiveSubscriptions(pending.getUser().getId(), today);
            if (active.isEmpty()) {
                pending.setStatus("Active");
            }
        }
    }
}
