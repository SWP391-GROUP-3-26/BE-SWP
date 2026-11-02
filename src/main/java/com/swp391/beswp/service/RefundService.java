package com.swp391.beswp.service;

import com.swp391.beswp.dto.RefundDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {
    private static final java.security.SecureRandom PICKUP_RANDOM = new java.security.SecureRandom();
    private static final String PICKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final RefundRequestRepository refunds;
    private final PaymentRepository payments;
    private final ClassRepository classes;
    private final ClassBookingRepository bookings;
    private final UserRepository users;
    private final ActivityLogRepository logs;
    private final Clock paymentClock;

    public CancellationPreview preview(Integer bookingId, List<ClassBooking> registration, List<Payment> attempts) {
        Set<Integer> cancelled = futureBookings(registration);
        List<ClassBooking> paidSessions = paidSessions(registration);
        int cancelledPaid = (int) paidSessions.stream().filter(b -> cancelled.contains(b.getId())).count();
        BigDecimal paid = BigDecimal.ZERO, expected = BigDecimal.ZERO;
        for (Payment p : attempts) if ("Paid".equals(p.getStatus())) {
            paid = paid.add(p.getAmount());
            if (!paidSessions.isEmpty()) expected = expected.add(estimate(p.getAmount(), paidSessions.size(), cancelledPaid));
        }
        return new CancellationPreview(bookingId, registration.size(), cancelled.size(), paid, expected,
                registration.stream().anyMatch(b -> "PACKAGE".equals(b.getPaymentMethod())), sessionViews(registration, cancelled),
                (int) registration.stream().filter(b -> "PACKAGE".equals(b.getPaymentMethod()) && cancelled.contains(b.getId())).count());
    }

    public Set<Integer> futureBookings(List<ClassBooking> registration) {
        LocalDateTime now = now();
        Set<Integer> ids = new HashSet<>();
        for (ClassBooking b : registration) if (("Booked".equals(b.getStatus()) || "Pending".equals(b.getStatus()))
                && b.getSession().getDate().atTime(b.getSession().getStartTime()).isAfter(now)) ids.add(b.getId());
        return ids;
    }

    public static BigDecimal estimate(BigDecimal paid, int total, int cancelled) {
        if (paid == null || paid.signum() < 0 || total <= 0 || cancelled < 0 || cancelled > total) {
            throw new IllegalArgumentException("Dữ liệu tính hoàn tiền không hợp lệ");
        }
        return paid.multiply(BigDecimal.valueOf(cancelled)).divide(BigDecimal.valueOf(total), 0, RoundingMode.DOWN);
    }

    /** Caller holds the class lock and cancels the same booking ids in this transaction. */
    @Transactional
    public void createForCancellation(Payment payment, List<ClassBooking> registration, Set<Integer> cancelled, User actor) {
        create(payment, registration, cancelled, actor, "Hủy các buổi tương lai của đăng ký lớp");
    }

    private RefundRequest create(Payment payment, List<ClassBooking> registration, Set<Integer> cancelled, User actor, String reason) {
        // Only cash-funded sessions enter the monetary refund; package rows release their own quota.
        registration = paidSessions(registration);
        Set<Integer> paidIds = registration.stream().map(ClassBooking::getId).collect(java.util.stream.Collectors.toSet());
        cancelled = cancelled.stream().filter(paidIds::contains).collect(java.util.stream.Collectors.toSet());
        if (registration.isEmpty() || cancelled.isEmpty()) return null;
        Optional<RefundRequest> existing = refunds.findByPayment_Id(payment.getId());
        if (existing.isPresent()) return existing.get();
        if (!"Paid".equals(payment.getStatus()) || payment.getBooking() == null || cancelled.isEmpty()) {
            throw error(HttpStatus.CONFLICT, "Giao dịch không có khoản hoàn tiền hợp lệ");
        }
        BigDecimal expected = estimate(payment.getAmount(), registration.size(), cancelled.size());
        if (expected.signum() == 0) return null;
        RefundRequest r = new RefundRequest();
        r.setPayment(payment); r.setRequestedBy(actor);
        r.setItemName(payment.getItemNameAtPayment() != null ? payment.getItemNameAtPayment() : payment.getBooking().getSession().getClassEntity().getName());
        r.setPaidAmount(payment.getAmount()); r.setEstimatedAmount(expected);
        r.setTotalSessions(registration.size()); r.setCancelledSessions(cancelled.size()); r.setReason(reason);
        r.setCreatedAt(now()); r.setUpdatedAt(now());
        for (ClassBooking b : registration) r.getSessions().add(new RefundSessionSnapshot(b.getSession().getId(),
                b.getSession().getDate(), b.getSession().getStartTime(), cancelled.contains(b.getId())));
        payment.setRequiresReview(true);
        refunds.saveAndFlush(r);
        log(actor, "REQUEST_REFUND", r, "Số tiền dự kiến " + expected + " VND; " + cancelled.size() + "/" + registration.size() + " buổi");
        return r;
    }

    @Transactional
    public RefundView createLegacy(Authentication auth, Integer paymentId) {
        User manager = actor(auth, "Center Manager");
        Integer classId = payments.findRefundClassId(paymentId).orElseThrow(this::notFound);
        classes.findByIdForUpdate(classId).orElseThrow(this::notFound);
        Payment p = payments.findById(paymentId).orElseThrow(this::notFound);
        Optional<RefundRequest> existing = refunds.findByPayment_Id(paymentId);
        if (existing.isPresent()) return view(existing.get(), true);
        if (!p.isRequiresReview() || !"Paid".equals(p.getStatus())) throw error(HttpStatus.CONFLICT, "Giao dịch không cần đối soát");
        List<ClassBooking> registration = registration(p.getBooking());
        Set<Integer> cancelled = cancelledIds(registration);
        RefundRequest r = create(p, registration, cancelled, manager,
                "Khởi tạo từ giao dịch cũ cần đối soát. Số tiền được dựng lại theo các buổi đã hủy; quản lý cần kiểm tra trước khi duyệt.");
        if (r == null) throw error(HttpStatus.CONFLICT, "Không có số tiền đủ điều kiện hoàn");
        return view(r, true);
    }

    public StaffQueue queue(Authentication auth, boolean manager) {
        actor(auth, manager ? "Center Manager" : "Receptionist");
        List<LegacyPayment> legacy = manager ? payments.findRefundCandidates().stream()
                .filter(p -> !cancelledIds(registration(p.getBooking())).isEmpty())
                .map(p -> new LegacyPayment(p.getId(), p.getBooking().getUser().getFullName(),
                        p.getItemNameAtPayment() != null ? p.getItemNameAtPayment() : p.getBooking().getSession().getClassEntity().getName(), p.getAmount())).toList() : List.of();
        return new StaffQueue(refunds.findAllByOrderByUpdatedAtDesc().stream().map(r -> view(r, false)).toList(), legacy);
    }

    private List<ClassBooking> paidSessions(List<ClassBooking> registration) {
        return registration.stream().filter(b -> !"PACKAGE".equals(b.getPaymentMethod())).toList();
    }

    public RefundView detail(Authentication auth, Integer id, String role) {
        User user = actor(auth, role);
        RefundRequest r = refunds.findById(id).orElseThrow(this::notFound);
        if ("Member".equals(role)) requireOwner(r, user);
        return view(r, true, "Member".equals(role));
    }

    @Transactional
    public RefundView review(Authentication auth, Integer id, Review input) {
        User manager = actor(auth, "Center Manager");
        RefundRequest r = locked(id, input.version());
        if (!"PENDING".equals(r.getStatus())) throw error(HttpStatus.CONFLICT, "Chỉ duyệt yêu cầu đang chờ");
        String note = clean(input.note());
        if ("REJECTED".equals(input.status())) {
            if (note.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Vui lòng ghi lý do từ chối");
            r.setApprovedAmount(null);
            r.getPayment().setRequiresReview(false);
        } else {
            if (input.approvedAmount() == null || input.approvedAmount().signum() <= 0 || input.approvedAmount().compareTo(r.getPaidAmount()) > 0) {
                throw error(HttpStatus.BAD_REQUEST, "Số tiền duyệt phải lớn hơn 0 và không vượt khoản thực thu");
            }
            if (input.approvedAmount().compareTo(r.getEstimatedAmount()) != 0 && note.isBlank()) {
                throw error(HttpStatus.BAD_REQUEST, "Vui lòng ghi lý do điều chỉnh số tiền hoàn");
            }
            r.setApprovedAmount(input.approvedAmount());
        }
        r.setStatus(input.status()); r.setReviewNote(note); r.setReviewedBy(manager); r.setReviewedAt(now());
        notifyMember(r);
        log(manager, "REVIEW_REFUND", r, input.status() + ": " + note);
        refunds.flush(); return view(r, true);
    }

    @Transactional
    public RefundView recipient(Authentication auth, Integer id, Recipient input) {
        User member = actor(auth, "Member");
        RefundRequest r = locked(id, input.version()); requireOwner(r, member);
        if (!"APPROVED".equals(r.getStatus())) throw error(HttpStatus.CONFLICT, "Chỉ được tạo mã nhận tiền khi yêu cầu hoàn tiền đã được duyệt");
        if (!"CASH".equals(input.method())) throw error(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ nhận tiền mặt tại quầy");
        if (r.getPickupCode() == null) r.setPickupCode(newPickupCode());
        r.setPayoutMethod("CASH"); r.setBankName(null); r.setAccountNumber(null); r.setAccountHolder(null);
        r.setUpdatedAt(now()); log(member, "UPDATE_REFUND_RECIPIENT", r, "Đăng ký nhận tiền mặt tại quầy");
        refunds.flush(); return view(r, true, true);
    }

    @Transactional
    public RefundView payout(Authentication auth, Integer id, Payout input) {
        User receptionist = actor(auth, "Receptionist");
        RefundRequest r = locked(id, input.version());
        if (!"APPROVED".equals(r.getStatus())) throw error(HttpStatus.CONFLICT, "Khoản hoàn chưa được duyệt hoặc đã chi trả");
        if (!"CASH".equals(r.getPayoutMethod()) || r.getPickupCode() == null) {
            throw error(HttpStatus.CONFLICT, "Học viên chưa đăng ký nhận tiền tại quầy và tạo mã hoàn tiền");
        }
        if (!"Paid".equals(r.getPayment().getStatus()) || r.getApprovedAmount() == null
                || r.getApprovedAmount().compareTo(r.getPayment().getAmount()) > 0) {
            throw error(HttpStatus.CONFLICT, "Khoản thực thu đã thay đổi, cần kiểm tra lại trước khi chi trả");
        }
        if (!java.security.MessageDigest.isEqual(normalizeCode(r.getPickupCode()), normalizeCode(input.pickupCode()))) {
            throw error(HttpStatus.BAD_REQUEST, "Mã hoàn tiền không đúng với yêu cầu này. Vui lòng kiểm tra mã member cung cấp.");
        }
        r.setPayoutReference("CASH-HT-" + r.getId()); r.setPayoutNote(clean(input.note()));
        r.setPaidBy(receptionist); r.setPaidAt(now()); r.setStatus("PAID"); r.getPayment().setRequiresReview(false);
        notifyMember(r); log(receptionist, "PAY_REFUND", r, "Đã chi " + r.getApprovedAmount() + " VND; biên nhận " + r.getPayoutReference());
        refunds.flush(); return view(r, true);
    }

    public List<RefundView> notifications(Authentication auth) {
        User member = actor(auth, "Member");
        return refunds.findUnread(member.getId()).stream().map(r -> view(r, false)).toList();
    }

    @Transactional
    public RefundView markRead(Authentication auth, Integer id, int revision) {
        User member = actor(auth, "Member");
        RefundRequest r = refunds.findByIdForUpdate(id).orElseThrow(this::notFound); requireOwner(r, member);
        if (revision <= 0 || revision > r.getNotificationRevision()) throw error(HttpStatus.BAD_REQUEST, "Phiên bản thông báo không hợp lệ");
        r.setReadRevision(Math.max(r.getReadRevision(), revision)); refunds.flush(); return view(r, true, true);
    }

    RefundView view(RefundRequest r, boolean detail) {
        return view(r, detail, false);
    }

    RefundView view(RefundRequest r, boolean detail, boolean owner) {
        User member = r.getPayment().getBooking().getUser();
        return new RefundView(r.getId(), r.getPayment().getId(), member.getId(), member.getFullName(), r.getItemName(), r.getStatus(),
                r.getPaidAmount(), r.getEstimatedAmount(), r.getApprovedAmount(), r.getTotalSessions(), r.getCancelledSessions(),
                r.getReason(), r.getReviewNote(), name(r.getReviewedBy()), r.getReviewedAt(), r.getPayoutMethod(),
                detail ? r.getBankName() : null, detail ? r.getAccountNumber() : null, detail ? r.getAccountHolder() : null,
                r.getPayoutReference(), r.getPayoutNote(), name(r.getPaidBy()), r.getPaidAt(), r.getCreatedAt(), r.getUpdatedAt(),
                r.getNotificationRevision(), r.getNotificationRevision() > r.getReadRevision(), r.getVersion(), detail ? r.getSessions().stream()
                .map(s -> new SessionView(s.getSessionId(), s.getDate(), s.getStartTime(), s.isRefundable())).toList() : List.of(),
                owner && detail && "APPROVED".equals(r.getStatus()) ? r.getPickupCode() : null,
                "CASH".equals(r.getPayoutMethod()) && r.getPickupCode() != null && "APPROVED".equals(r.getStatus()));
    }

    private String newPickupCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder("HT");
            for (int i = 0; i < 16; i++) {
                if (i % 4 == 0) builder.append('-');
                builder.append(PICKUP_ALPHABET.charAt(PICKUP_RANDOM.nextInt(PICKUP_ALPHABET.length())));
            }
            code = builder.toString();
        } while (refunds.existsByPickupCode(code));
        return code;
    }

    private byte[] normalizeCode(String code) {
        return clean(code).toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    User actor(Authentication auth, String role) {
        if (auth == null || !auth.isAuthenticated()) throw error(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        if (auth.getAuthorities().stream().noneMatch(a -> ("ROLE_" + role).equals(a.getAuthority()))) throw error(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
        int id;
        try { id = Integer.parseInt(auth.getName()); } catch (NumberFormatException ex) { throw error(HttpStatus.UNAUTHORIZED, "Danh tính không hợp lệ"); }
        User u = users.findById(id).orElseThrow(this::notFound);
        if (u.getRole() == null || !role.equalsIgnoreCase(u.getRole().getRoleName()) || !"Active".equalsIgnoreCase(u.getStatus())) {
            throw error(HttpStatus.FORBIDDEN, "Tài khoản không hoạt động hoặc không đúng vai trò");
        }
        return u;
    }
    private RefundRequest locked(Integer id, Long version) {
        RefundRequest r = refunds.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (version == null || r.getVersion() != version) throw error(HttpStatus.CONFLICT, "Yêu cầu đã thay đổi. Vui lòng tải lại trước khi lưu");
        return r;
    }
    private void requireOwner(RefundRequest r, User member) {
        if (!r.getPayment().getBooking().getUser().getId().equals(member.getId())) throw notFound();
    }
    private List<ClassBooking> registration(ClassBooking root) {
        return root.getRegistrationCode() == null ? List.of(root) : bookings.findByRegistrationCodeOrderByIdAsc(root.getRegistrationCode());
    }
    private Set<Integer> cancelledIds(List<ClassBooking> registration) {
        Set<Integer> result = new HashSet<>();
        registration.stream().filter(b -> "Cancelled".equals(b.getStatus())).forEach(b -> result.add(b.getId())); return result;
    }
    private List<SessionView> sessionViews(List<ClassBooking> registration, Set<Integer> ids) {
        return registration.stream().map(b -> new SessionView(b.getSession().getId(), b.getSession().getDate(), b.getSession().getStartTime(), ids.contains(b.getId()))).toList();
    }
    private void notifyMember(RefundRequest r) { r.setNotificationRevision(r.getNotificationRevision() + 1); r.setUpdatedAt(now()); }
    private void log(User actor, String action, RefundRequest r, String detail) {
        logs.save(ActivityLog.builder().user(actor).actionType(action).targetEntity("Payment")
                .description("Hoàn tiền #" + r.getId() + " / thanh toán #" + r.getPayment().getId() + ": " + detail).createdAt(now()).build());
    }
    private String clean(String s) { return s == null ? "" : s.trim(); }
    private String name(User u) { return u == null ? null : u.getFullName(); }
    private LocalDateTime now() { return LocalDateTime.now(paymentClock); }
    private ResponseStatusException notFound() { return error(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hoàn tiền hoặc thanh toán"); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
