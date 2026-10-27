package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipLifecycleServiceTest {
    final MemberPackageSubscriptionRepository subscriptions = mock(MemberPackageSubscriptionRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final EntityManager em = mock(EntityManager.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-26T03:00:00Z"), VnpayService.OFFSET);
    final MembershipLifecycleService service = new MembershipLifecycleService(subscriptions, users, em, clock);

    @Test void paidQueuedMembershipStartsWhenThePreviousTermEnds() {
        User user = new User(); user.setId(7);
        var old = MemberSubscription.builder().id(1).user(user).status("Active").endDate(LocalDate.now(clock).minusDays(1)).build();
        var next = MemberSubscription.builder().id(2).user(user).status("Pending").endDate(LocalDate.now(clock).plusDays(29)).build();
        when(subscriptions.findExpiredActiveSubscriptions(LocalDate.now(clock))).thenReturn(List.of(old));
        when(subscriptions.findDuePendingSubscriptions(LocalDate.now(clock))).thenReturn(List.of(next));
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        when(subscriptions.hasActiveSubscription(7, LocalDate.now(clock))).thenAnswer(call -> "Active".equals(old.getStatus()));
        service.updateStatuses();
        assertEquals("Expired", old.getStatus()); assertEquals("Active", next.getStatus());
    }

    @Test void cancellationWhileWaitingForMemberLockIsPreserved() {
        User user = new User(); user.setId(7);
        var next = MemberSubscription.builder().id(2).user(user).status("Pending").endDate(LocalDate.now(clock).plusDays(29)).build();
        when(subscriptions.findDuePendingSubscriptions(LocalDate.now(clock))).thenReturn(List.of(next));
        when(users.findByIdForUpdate(7)).thenAnswer(call -> { next.setStatus("Cancelled"); return Optional.of(user); });
        service.updateStatuses();
        assertEquals("Cancelled", next.getStatus());
        verify(subscriptions, never()).hasActiveSubscription(7, LocalDate.now(clock));
    }
}
