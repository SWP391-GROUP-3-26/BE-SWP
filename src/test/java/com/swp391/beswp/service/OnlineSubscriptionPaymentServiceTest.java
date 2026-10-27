package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineSubscriptionPaymentServiceTest {
    final PaymentRepository payments = mock(PaymentRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final MemberPackageSubscriptionRepository subscriptions = mock(MemberPackageSubscriptionRepository.class);
    final AuditLogService audit = mock(AuditLogService.class);
    final EntityManager em = mock(EntityManager.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), VnpayService.OFFSET);
    final OnlineSubscriptionPaymentService service = new OnlineSubscriptionPaymentService(payments, users, subscriptions, audit, clock, em);

    Payment pending() {
        User member = new User(); member.setId(7); member.setStatus("Active");
        User actor = new User(); actor.setId(2);
        MemberSubscription subscription = MemberSubscription.builder().id(10).user(member).status("Pending")
                .startDate(LocalDate.now(clock))
                .endDate(LocalDate.now(clock).plusDays(30)).build();
        return Payment.builder().id(20).subscription(subscription).receptionist(actor).method("Card")
                .amount(new BigDecimal("100000")).expiresAt(LocalDateTime.now(clock).plusMinutes(15)).build();
    }

    @Test void onlyConfirmedPaymentActivatesPackageAndLogsReceptionist() {
        Payment p = pending();
        assertEquals("Pending", p.getSubscription().getStatus());
        service.confirm(p, true);
        assertEquals("Paid", p.getStatus()); assertEquals("Active", p.getSubscription().getStatus());
        assertFalse(p.isRequiresReview());
        verify(audit).recordLog(eq(p.getReceptionist()), eq("CONFIRM_PAYMENT"), eq("Payment"), contains("#7"));
    }

    @Test void latePaymentIsPaidButRequiresReviewWithoutActivatingPackage() {
        Payment p = pending(); p.setExpiresAt(LocalDateTime.now(clock).minusSeconds(1));
        service.confirm(p, true);
        assertEquals("Paid", p.getStatus()); assertTrue(p.isRequiresReview());
        assertEquals("Cancelled", p.getSubscription().getStatus());
    }

    @Test void activePackageBlocksCallbackActivationAndFailedCallbackCancelsPending() {
        Payment p = pending();
        when(subscriptions.hasActiveSubscription(7, LocalDate.now(clock))).thenReturn(true);
        service.confirm(p, true);
        assertTrue(p.isRequiresReview()); assertEquals("Cancelled", p.getSubscription().getStatus());
        p = pending(); service.confirm(p, false);
        assertEquals("Failed", p.getStatus()); assertFalse(p.isRequiresReview());
        assertEquals("Cancelled", p.getSubscription().getStatus());
    }

    @Test void expiredPendingOrderReleasesPurchaseHoldWithoutDeletingHistory() {
        Payment p = pending(); p.setExpiresAt(LocalDateTime.now(clock));
        when(payments.findAllBySubscription_User_IdAndStatus(7, "Pending")).thenReturn(List.of(p));
        assertFalse(service.hasPending(7));
        service.expirePending(7);
        assertEquals("Failed", p.getStatus()); assertEquals("Cancelled", p.getSubscription().getStatus());
        verify(payments, never()).delete(any());
    }

    @Test void callbackRefreshesPaymentAndSubscriptionAfterObtainingMemberLock() {
        Payment p = pending();
        when(payments.findById(20)).thenReturn(Optional.of(p));
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(p.getSubscription().getUser()));
        assertSame(p, service.lock(20));
        var order = inOrder(users, em);
        order.verify(users).findByIdForUpdate(7);
        order.verify(em).refresh(p);
        order.verify(em).refresh(p.getSubscription());
    }

    Payment renewal(String start) {
        Payment p = pending();
        var previous = MemberSubscription.builder().id(9).user(p.getSubscription().getUser()).status("Active")
                .startDate(LocalDate.now(clock).minusDays(5)).endDate(LocalDate.now(clock).plusDays(25)).build();
        p.setRenewalSubscription(previous); p.setRenewalStart(start);
        when(subscriptions.findCurrentActiveSubscriptions(7, LocalDate.now(clock))).thenReturn(List.of(previous));
        if ("AFTER_CURRENT".equals(start)) {
            p.getSubscription().setStartDate(previous.getEndDate().plusDays(1));
            p.getSubscription().setEndDate(previous.getEndDate().plusDays(30));
        }
        return p;
    }

    @Test void immediateRenewalExpiresOnlyItsOriginalMembershipAfterPayment() {
        Payment p = renewal("IMMEDIATE");
        service.confirm(p, true);
        assertEquals("Active", p.getSubscription().getStatus());
        assertEquals("Expired", p.getRenewalSubscription().getStatus()); assertFalse(p.isRequiresReview());
        service.confirm(p, true);
        assertFalse(p.isRequiresReview(), "Duplicate callback must preserve the confirmed result");
    }

    @Test void paidFutureRenewalWaitsForItsStartDateAndKeepsOldMembershipActive() {
        Payment p = renewal("AFTER_CURRENT");
        service.confirm(p, true);
        assertEquals("Paid", p.getStatus()); assertFalse(p.isRequiresReview());
        assertEquals("Pending", p.getSubscription().getStatus()); assertEquals("Active", p.getRenewalSubscription().getStatus());
    }

    @Test void lateOrFailedRenewalNeverExpiresTheExistingMembership() {
        Payment p = renewal("IMMEDIATE"); p.setExpiresAt(LocalDateTime.now(clock));
        service.confirm(p, true);
        assertTrue(p.isRequiresReview()); assertEquals("Cancelled", p.getSubscription().getStatus());
        assertEquals("Active", p.getRenewalSubscription().getStatus());
        p = renewal("IMMEDIATE"); service.confirm(p, false);
        assertEquals("Active", p.getRenewalSubscription().getStatus()); assertEquals("Failed", p.getStatus());
    }
}
