package com.swp391.beswp.service;

import com.swp391.beswp.dto.MembershipFreezeResponse;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class MembershipFreezeService {
    private final MemberPackageSubscriptionRepository subscriptions;
    private final UserRepository users;
    private final ClassBookingRepository bookings;
    private final PaymentRepository payments;
    private final AuditLogService audit;
    private final Clock paymentClock;
    private final EntityManager entityManager;

    @Transactional
    public MembershipFreezeResponse freeze(String principal, Integer subscriptionId, Integer months) {
        User member = lockMember(principal);
        MemberSubscription sub = owned(subscriptionId, member);
        LocalDateTime now = now();
        if (!"Active".equals(sub.getStatus()) || sub.getStartDate().isAfter(now.toLocalDate())
                || sub.getEndDate().isBefore(now.toLocalDate())) throw conflict("Chỉ được bảo lưu gói đang có hiệu lực");
        if (subscriptions.findCurrentActiveSubscriptions(member.getId(), now.toLocalDate()).stream()
                .anyMatch(s -> !s.getId().equals(sub.getId()))) {
            throw conflict("Tài khoản đang có nhiều gói hoạt động; cần xử lý trước khi bảo lưu");
        }
        int budget = value(sub.getFreezeMonthsAtPurchase());
        int used = value(sub.getFreezeMonthsUsed());
        if (months == null || months < 1 || budget == 0 || months > budget - used
                || sub.getDurationAtPurchase() == null
                || sub.getDurationAtPurchase() < MembershipFreezePolicy.MINIMUM_DURATION_DAYS) {
            throw conflict("Gói không đủ điều kiện hoặc không còn đủ số tháng bảo lưu; mỗi lần tối thiểu 1 tháng");
        }
        if (payments.findAllBySubscription_User_IdAndStatus(member.getId(), "Pending").stream()
                .anyMatch(p -> p.getExpiresAt() != null && p.getExpiresAt().isAfter(now))) {
            throw conflict("Vui lòng hoàn tất hoặc hủy thanh toán gói đang chờ trước khi bảo lưu");
        }
        LocalDateTime until;
        try {
            until = now.plusMonths(months);
            requireDateRange(sub.getEndDate().plusDays(ChronoUnit.DAYS.between(now, until)));
        } catch (DateTimeException | ArithmeticException e) {
            throw conflict("Thời gian bảo lưu vượt phạm vi ngày hợp lệ");
        }
        for (var booking : bookings.findReservedBookingsForFreeze(member.getId(), now)) {
            var session = booking.getSession();
            if (ClassSchedule.withinTerm(session.getClassEntity(), session.getDate())
                    && session.getDate().atTime(session.getStartTime()).isBefore(until)
                    && session.getDate().atTime(session.getEndTime()).isAfter(now)) {
                throw conflict("Đã có lớp đăng ký trong thời gian bảo lưu: " + session.getClassEntity().getName());
            }
        }
        sub.setFreezeMonthsUsed(used + months);
        sub.setFreezeStartedAt(now); sub.setFreezeEndsAt(until); sub.setStatus("Frozen");
        audit.recordLog(member, "FREEZE_MEMBERSHIP", "Member_Subscription",
                "Bảo lưu #" + sub.getId() + ": " + months + " tháng; " + now + " đến " + until);
        return response(sub, 0);
    }

    @Transactional
    public MembershipFreezeResponse resume(String principal, Integer subscriptionId) {
        User member = lockMember(principal);
        MemberSubscription sub = owned(subscriptionId, member);
        if (!"Frozen".equals(sub.getStatus())) throw conflict("Gói không trong thời gian bảo lưu");
        return response(sub, finish(sub, now(), member));
    }

    @Transactional
    public MembershipFreezeResponse state(String principal, Integer subscriptionId) {
        User member = lockMember(principal);
        MemberSubscription sub = owned(subscriptionId, member);
        LocalDateTime now = now();
        long days = "Frozen".equals(sub.getStatus()) && !sub.getFreezeEndsAt().isAfter(now) ? finish(sub, now, member) : 0;
        return response(sub, days);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void resumeDueFreezes() {
        LocalDateTime now = now();
        for (MemberSubscription sub : subscriptions.findDueFrozenSubscriptions(now)) {
            User member = users.findByIdForUpdate(sub.getUser().getId()).orElseThrow();
            entityManager.refresh(sub);
            if ("Frozen".equals(sub.getStatus()) && !sub.getFreezeEndsAt().isAfter(now)) finish(sub, now, member);
        }
    }

    private long finish(MemberSubscription sub, LocalDateTime now, User member) {
        LocalDateTime until = now.isBefore(sub.getFreezeEndsAt()) ? now : sub.getFreezeEndsAt();
        long days = Math.max(0, ChronoUnit.DAYS.between(sub.getFreezeStartedAt(), until));
        LocalDate oldEnd = sub.getEndDate();
        LocalDate extended = oldEnd.plusDays(days);
        requireDateRange(extended);
        for (MemberSubscription queued : subscriptions.findPaidQueuedSubscriptions(member.getId(), oldEnd)) {
            requireDateRange(queued.getEndDate().plusDays(days));
            queued.setStartDate(queued.getStartDate().plusDays(days));
            queued.setEndDate(queued.getEndDate().plusDays(days));
        }
        sub.setEndDate(extended);
        sub.setStatus(extended.isBefore(now.toLocalDate()) ? "Expired" : "Active");
        LocalDateTime started = sub.getFreezeStartedAt();
        sub.setFreezeStartedAt(null); sub.setFreezeEndsAt(null);
        audit.recordLog(member, "RESUME_MEMBERSHIP", "Member_Subscription",
                "Tham gia lại #" + sub.getId() + "; bảo lưu từ " + started + " đến " + until
                        + "; cộng " + days + " ngày; không hoàn số tháng bảo lưu đã sử dụng");
        return days;
    }

    private User lockMember(String principal) {
        final int id;
        try { id = Integer.parseInt(principal); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
        User user = users.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getRole() == null || !"Member".equalsIgnoreCase(user.getRole().getRoleName())
                || !"Active".equalsIgnoreCase(user.getStatus())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    private MemberSubscription owned(Integer id, User member) {
        return subscriptions.findByIdAndUser_Id(id, member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gói của hội viên"));
    }

    private MembershipFreezeResponse response(MemberSubscription sub, long days) {
        int budget = value(sub.getFreezeMonthsAtPurchase()), used = value(sub.getFreezeMonthsUsed());
        return new MembershipFreezeResponse(true, sub.getId(), sub.getStatus(), budget, used, Math.max(0, budget - used),
                sub.getFreezeStartedAt(), sub.getFreezeEndsAt(), sub.getEndDate(), days, MembershipFreezePolicy.TERMS);
    }

    private int value(Integer n) { return n == null ? 0 : n; }
    private LocalDateTime now() { return LocalDateTime.now(paymentClock).withNano(0); }
    private void requireDateRange(LocalDate date) {
        if (date.getYear() < 1 || date.getYear() > 9999) throw conflict("Ngày hết hạn vượt phạm vi hợp lệ");
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
