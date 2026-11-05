package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberNotificationDtos;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.MemberNotification;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.Role;
import com.swp391.beswp.entity.TrainingPlan;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.MemberNotificationRepository;
import com.swp391.beswp.repository.MemberSubscriptionRepository;
import com.swp391.beswp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberNotificationServiceTest {

    private final MemberNotificationRepository notifications = mock(MemberNotificationRepository.class);
    private final MemberSubscriptionRepository subscriptions = mock(MemberSubscriptionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-11-05T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final Authentication authentication = new UsernamePasswordAuthenticationToken(
            "1", null, List.of(new SimpleGrantedAuthority("ROLE_Member")));
    private MemberNotificationService service;
    private User member;

    @BeforeEach
    void setUp() {
        service = new MemberNotificationService(notifications, subscriptions, users, clock, 7);
        member = user(1, "Member", "Nguyễn Minh Anh");
        when(users.findById(1)).thenReturn(Optional.of(member));
        when(subscriptions.findExpiringSubscriptions(eq(1), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
    }

    @Test
    void unreadSummaryCreatesOneExpiryNotificationAndShowsBellBadge() {
        MemberSubscription subscription = new MemberSubscription();
        subscription.setId(10);
        subscription.setUser(member);
        subscription.setPackageNameAtPurchase("Gói Kim Cương");
        subscription.setEndDate(LocalDate.of(2026, 11, 10));
        when(subscriptions.findExpiringSubscriptions(
                1, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 12)))
                .thenReturn(List.of(subscription));
        when(notifications.countByMember_IdAndReadAtIsNull(1)).thenReturn(1L);

        MemberNotificationDtos.UnreadSummary result = service.unreadSummary(authentication);

        assertEquals(1L, result.unreadCount());
        assertTrue(result.hasUnread());
        verify(notifications).insertPackageExpiryIfAbsent(
                eq(1), eq(10), eq("Gói dịch vụ sắp hết hạn"),
                eq("Gói dịch vụ \"Gói Kim Cương\" còn 5 ngày và sẽ hết hạn vào 2026-11-10."),
                eq("PACKAGE_EXPIRING:10:2026-11-10"), any(LocalDateTime.class));
    }

    @Test
    void readingLastNotificationRemovesBellBadge() {
        MemberNotification notification = new MemberNotification();
        notification.setId(50L);
        notification.setMember(member);
        notification.setType("TRAINING_PLAN_ASSIGNED");
        notification.setTitle("Bạn vừa nhận bài tập");
        notification.setMessage("HLV đã giao kế hoạch tập luyện.");
        notification.setReferenceType("TRAINING_PLAN");
        notification.setReferenceId(20);
        notification.setSourceKey("TRAINING_PLAN:20:1:1");
        notification.setCreatedAt(LocalDateTime.of(2026, 11, 5, 10, 0));
        when(notifications.findByIdAndMember_Id(50L, 1)).thenReturn(Optional.of(notification));
        when(notifications.saveAndFlush(notification)).thenReturn(notification);
        when(notifications.countByMember_IdAndReadAtIsNull(1)).thenReturn(0L);

        MemberNotificationDtos.ReadResult result = service.markRead(authentication, 50L);

        assertTrue(result.notification().read());
        assertEquals(0L, result.unreadCount());
        assertFalse(result.hasUnread());
    }

    @Test
    void publishingClassPlanCreatesNotificationForEveryRecipient() {
        User secondMember = user(2, "Member", "Lê Thu Thảo");
        User coach = user(3, "Coach", "Alex Trần");
        ClassEntity classEntity = ClassEntity.builder().id(30).name("Yoga cơ bản").coach(coach).build();
        TrainingPlan plan = new TrainingPlan();
        plan.setId(20);
        plan.setTitle("Bài tập tuần 1");
        plan.setClassEntity(classEntity);
        plan.setCreatedBy(coach);
        plan.setPublishedRevision(2);
        plan.setPublishedAt(LocalDateTime.of(2026, 11, 5, 10, 0));

        service.notifyTrainingPlan(plan, List.of(member, secondMember));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MemberNotification>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(notifications).saveAll(captor.capture());
        List<MemberNotification> rows = new java.util.ArrayList<>();
        captor.getValue().forEach(rows::add);
        assertEquals(2, rows.size());
        assertEquals("TRAINING_PLAN:20:2:1", rows.get(0).getSourceKey());
        assertEquals("TRAINING_PLAN:20:2:2", rows.get(1).getSourceKey());
    }

    private User user(int id, String roleName, String fullName) {
        Role role = new Role();
        role.setRoleName(roleName);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setFullName(fullName);
        user.setStatus("Active");
        return user;
    }
}
