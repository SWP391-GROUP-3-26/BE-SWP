package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberNotificationDtos;
import com.swp391.beswp.entity.MemberNotification;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.TrainingPlan;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.MemberNotificationRepository;
import com.swp391.beswp.repository.MemberSubscriptionRepository;
import com.swp391.beswp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberNotificationService {

    private static final String TYPE_TRAINING_PLAN = "TRAINING_PLAN_ASSIGNED";
    private static final String TYPE_PACKAGE_EXPIRING = "PACKAGE_EXPIRING";

    private final MemberNotificationRepository notifications;
    private final MemberSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final Clock clock;
    private final int packageExpiryDays;

    public MemberNotificationService(
            MemberNotificationRepository notifications,
            MemberSubscriptionRepository subscriptions,
            UserRepository users,
            Clock paymentClock,
            @Value("${app.notification.package-expiry-days:7}") int packageExpiryDays
    ) {
        this.notifications = notifications;
        this.subscriptions = subscriptions;
        this.users = users;
        this.clock = paymentClock;
        this.packageExpiryDays = Math.max(1, packageExpiryDays);
    }

    @Transactional
    public MemberNotificationDtos.NotificationPage list(Authentication authentication, int page, int size) {
        User member = member(authentication);
        validatePage(page, size);
        materializePackageExpiryNotifications(member);

        Page<MemberNotification> result = notifications.findByMember_IdOrderByCreatedAtDescIdDesc(
                member.getId(), PageRequest.of(page, size));
        long unread = notifications.countByMember_IdAndReadAtIsNull(member.getId());
        return new MemberNotificationDtos.NotificationPage(
                result.getContent().stream().map(this::view).toList(),
                page,
                size,
                result.getTotalElements(),
                result.hasNext(),
                unread,
                unread > 0
        );
    }

    @Transactional
    public MemberNotificationDtos.UnreadSummary unreadSummary(Authentication authentication) {
        User member = member(authentication);
        materializePackageExpiryNotifications(member);
        long unread = notifications.countByMember_IdAndReadAtIsNull(member.getId());
        return new MemberNotificationDtos.UnreadSummary(unread, unread > 0);
    }

    @Transactional
    public MemberNotificationDtos.ReadResult markRead(Authentication authentication, Long notificationId) {
        User member = member(authentication);
        materializePackageExpiryNotifications(member);
        MemberNotification notification = notifications.findByIdAndMember_Id(notificationId, member.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(now());
            notifications.saveAndFlush(notification);
        }
        long unread = notifications.countByMember_IdAndReadAtIsNull(member.getId());
        return new MemberNotificationDtos.ReadResult(view(notification), unread, unread > 0);
    }

    @Transactional
    public void notifyTrainingPlan(TrainingPlan plan, Collection<User> recipients) {
        if (plan.getId() == null || plan.getPublishedRevision() < 1 || recipients.isEmpty()) {
            return;
        }

        String title = limit("Bạn vừa nhận bài tập: " + plan.getTitle(), 200);
        String message = limit("HLV " + plan.getCreatedBy().getFullName()
                + " đã giao kế hoạch tập luyện cho lớp " + plan.getClassEntity().getName() + ".", 1000);
        LocalDateTime createdAt = plan.getPublishedAt() == null ? now() : plan.getPublishedAt();
        List<MemberNotification> rows = recipients.stream().map(recipient -> {
            MemberNotification notification = new MemberNotification();
            notification.setMember(recipient);
            notification.setType(TYPE_TRAINING_PLAN);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setReferenceType("TRAINING_PLAN");
            notification.setReferenceId(plan.getId());
            notification.setSourceKey("TRAINING_PLAN:" + plan.getId()
                    + ":" + plan.getPublishedRevision() + ":" + recipient.getId());
            notification.setCreatedAt(createdAt);
            return notification;
        }).toList();
        notifications.saveAll(rows);
    }

    private void materializePackageExpiryNotifications(User member) {
        LocalDate today = LocalDate.now(clock);
        LocalDate cutoff = today.plusDays(packageExpiryDays);
        for (MemberSubscription subscription : subscriptions.findExpiringSubscriptions(
                member.getId(), today, cutoff)) {
            long remainingDays = ChronoUnit.DAYS.between(today, subscription.getEndDate());
            String packageName = subscription.getPackageNameAtPurchase();
            String title = "Gói dịch vụ sắp hết hạn";
            String message = remainingDays == 0
                    ? "Gói dịch vụ \"" + packageName + "\" sẽ hết hạn hôm nay."
                    : "Gói dịch vụ \"" + packageName + "\" còn " + remainingDays
                    + " ngày và sẽ hết hạn vào " + subscription.getEndDate() + ".";
            String sourceKey = "PACKAGE_EXPIRING:" + subscription.getId()
                    + ":" + subscription.getEndDate();
            notifications.insertPackageExpiryIfAbsent(
                    member.getId(),
                    subscription.getId(),
                    title,
                    limit(message, 1000),
                    sourceKey,
                    now()
            );
        }
    }

    private User member(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_Member".equals(authority.getAuthority()))) {
            throw error(HttpStatus.FORBIDDEN, "Chức năng thông báo chỉ dành cho Member");
        }
        final int userId;
        try {
            userId = Integer.parseInt(authentication.getName());
        } catch (NumberFormatException exception) {
            throw error(HttpStatus.UNAUTHORIZED, "Danh tính đăng nhập không hợp lệ");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
        if (user.getRole() == null || !"Member".equalsIgnoreCase(user.getRole().getRoleName())
                || !"Active".equalsIgnoreCase(user.getStatus())) {
            throw error(HttpStatus.FORBIDDEN, "Tài khoản không hoạt động hoặc không đúng vai trò Member");
        }
        return user;
    }

    private MemberNotificationDtos.NotificationView view(MemberNotification notification) {
        return new MemberNotificationDtos.NotificationView(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "page phải từ 0 và size phải từ 1 đến 100");
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
