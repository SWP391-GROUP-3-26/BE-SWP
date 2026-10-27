package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberPaymentResponse;
import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import com.swp391.beswp.entity.ActivityLog;
import com.swp391.beswp.repository.ActivityLogRepository;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MemberPaymentService {
    private final PaymentRepository payments;
    private final ClassRepository classes;
    private final ClassBookingRepository bookings;
    private final ClassReservationService reservations;
    private final MemberClassBookingService memberBookings;
    private final VnpayService vnpay;
    private final SepayService sepay;
    private final Clock paymentClock;
    private final OnlineSubscriptionPaymentService subscriptionPayments;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ActivityLogRepository activityLogRepository;

    @Transactional
    public MemberPaymentResponse getPayment(String principal, Integer paymentId) {
        User user = memberBookings.findMemberUser(principal);
        Integer subscriptionMemberId = payments.findOnlineSubscriptionMemberId(paymentId).orElse(null);
        if (subscriptionMemberId != null) {
            if (!Objects.equals(subscriptionMemberId, user.getId())) throw notFound();
            Payment payment = subscriptionPayments.lock(paymentId);
            subscriptionPayments.expirePending(user.getId());
            return MemberPaymentResponse.builder().success(true).paymentId(payment.getId())
                    .memberId(user.getId()).memberName(user.getFullName()).itemName(payment.getSubscription().getMembershipPackage().getName())
                    .paymentType("Subscription").paymentStatus(payment.getStatus()).bookingStatus(subscriptionDisplayStatus(payment))
                    .paymentAmount(payment.getAmount()).expiresAt(payment.getExpiresAt().atOffset(VnpayService.OFFSET))
                    .requiresReview(payment.isRequiresReview()).qrPayment(sepay.qrPayment(payment)).build();
        }
        Integer classId = payments.findOnlineClassId(paymentId).orElseThrow(this::notFound);
        classes.findByIdForUpdate(classId).orElseThrow(this::notFound);
        Payment payment = payments.findById(paymentId).orElseThrow(this::notFound);
        if (!Objects.equals(payment.getBooking().getUser().getId(), user.getId())) throw notFound();
        reservations.expirePending(classId, now());
        if (ClassSchedule.expired(payment.getBooking().getSession().getClassEntity(), now().toLocalDate())) {
            if ("Pending".equals(payment.getStatus())) payment.setStatus("Failed");
            reservations.cancelPendingRegistration(payment.getBooking());
        }
        return MemberPaymentResponse.builder().success(true)
                .paymentId(payment.getId()).bookingId(payment.getBooking().getId())
                .memberId(user.getId()).memberName(user.getFullName()).itemName(payment.getBooking().getSession().getClassEntity().getName())
                .paymentStatus(payment.getStatus()).bookingStatus(displayStatus(ClassSchedule.bookingStatus(payment.getBooking(), now().toLocalDate())))
                .paymentAmount(payment.getAmount()).expiresAt(payment.getExpiresAt().atOffset(VnpayService.OFFSET))
                .requiresReview(payment.isRequiresReview()).qrPayment(sepay.qrPayment(payment)).build();
    }

    @Transactional(readOnly = true)
    public String returnUrl(Map<String, String> params) {
        if (!vnpay.validSignature(params) || !vnpay.validMerchant(params)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phản hồi VNPay không hợp lệ");
        }
        Integer id = paymentId(params);
        if (id == null) throw notFound();
        Payment candidate = payments.findById(id).orElseThrow(this::notFound);
        if (candidate.getSubscription() != null && "Card".equals(candidate.getMethod()) && candidate.getExpiresAt() != null) {
            if (!validAmount(params, candidate)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền VNPay không hợp lệ");
            return resultUrl(candidate);
        }
        if (payments.findVnpayClassId(id).isEmpty()) throw notFound();
        Payment payment = payments.findById(id).orElseThrow(this::notFound);
        if (!validAmount(params, payment)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền VNPay không hợp lệ");
        }
        if (reservations.registrationBookings(payment.getBooking()).stream().anyMatch(b ->
                !ClassSchedule.bookable(b.getSession().getClassEntity(), b.getSession(), now()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buổi học không còn hiệu lực");
        }
        return resultUrl(payment);
    }

    @Transactional
    public Map<String, String> processIpn(Map<String, String> params) {
        if (!vnpay.validSignature(params)) return response("97", "Invalid signature");
        if (!vnpay.validMerchant(params)) return response("01", "Order not found");
        Integer id = paymentId(params);
        Integer subscriptionMemberId = id == null ? null : payments.findOnlineSubscriptionMemberId(id).orElse(null);
        if (subscriptionMemberId != null) {
            Payment payment = subscriptionPayments.lock(id);
            if (!"Card".equals(payment.getMethod())) return response("01", "Order not found");
            if (!validAmount(params, payment)) return response("04", "invalid amount");
            if (!validResult(params)) return response("99", "Invalid request");
            if (payment.getVnpResponseCode() != null) return response("02", "Order already confirmed");
            boolean success = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
            payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
            payment.setVnpResponseCode(params.get("vnp_ResponseCode"));
            payment.setVnpTransactionStatus(params.get("vnp_TransactionStatus"));
            payment.setVnpPayDate(params.get("vnp_PayDate"));
            subscriptionPayments.confirm(payment, success);
            return response("00", "Confirm Success");
        }
        Integer classId = id == null ? null : payments.findVnpayClassId(id).orElse(null);
        if (classId == null) return response("01", "Order not found");
        ClassEntity classEntity = classes.findByIdForUpdate(classId).orElse(null);
        if (classEntity == null) return response("01", "Order not found");
        // All reservation/payment writers acquire this class lock before loading mutable entities.
        Payment payment = payments.findById(id).orElseThrow(this::notFound);
        if (!validAmount(params, payment)) return response("04", "invalid amount");
        if (!validResult(params)) return response("99", "Invalid request");
        if (payment.getVnpResponseCode() != null) return response("02", "Order already confirmed");

        LocalDateTime now = now();
        reservations.expirePending(classId, now);
        ClassBooking booking = payment.getBooking();
        var registration = reservations.registrationBookings(booking);
        boolean latestAttempt = payments.findAllByBooking_IdOrderByIdDesc(booking.getId()).getFirst().getId().equals(id);
        boolean success = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
        boolean canConfirm = latestAttempt && "Pending".equals(payment.getStatus())
                && "Pending".equals(booking.getStatus()) && payment.getExpiresAt().isAfter(now)
                && booking.getPaymentExpiresAt() != null && booking.getPaymentExpiresAt().isAfter(now)
                && !registration.isEmpty() && registration.stream().allMatch(b -> "Pending".equals(b.getStatus())
                    && b.getPaymentExpiresAt() != null && b.getPaymentExpiresAt().isAfter(now)
                    && ClassSchedule.bookable(classEntity, b.getSession(), now)
                    && bookings.countReservedMembersBySessionId(b.getSession().getId(), now) <= classEntity.getMaxCapacity());

        payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        payment.setVnpResponseCode(params.get("vnp_ResponseCode"));
        payment.setVnpTransactionStatus(params.get("vnp_TransactionStatus"));
        payment.setVnpPayDate(params.get("vnp_PayDate"));
        payment.setStatus(success ? "Paid" : "Failed");
        payment.setRequiresReview(success && !canConfirm);
        if (success && canConfirm) {
            for (ClassBooking item : registration) {
                item.setStatus("Booked");
                item.setPaymentExpiresAt(null);
            }
            if (activityLogRepository != null) {
                try {
                    activityLogRepository.save(ActivityLog.builder()
                            .user(booking.getUser())
                            .actionType("CONFIRM_PAYMENT")
                            .targetEntity("Payment")
                            .description(String.format("Xác nhận thanh toán VNPay #PAY-%s thành công cho lớp '%s' số tiền %s VND",
                                    payment.getId(), classEntity.getName(), payment.getAmount()))
                            .createdAt(now)
                            .build());
                } catch (Exception ignored) {}
            }
        } else if (latestAttempt && "Pending".equals(booking.getStatus())) {
            reservations.cancelPendingRegistration(booking);
        }
        return response("00", "Confirm Success");
    }

    private String resultUrl(Payment payment) {
        String url = vnpay.frontendResultUrl(payment.getId());
        if (payment.getSubscription() != null) url += "&type=package";
        if (payment.getReceptionist() == null) return url;
        Integer memberId = payment.getBooking() != null ? payment.getBooking().getUser().getId()
                : payment.getSubscription().getUser().getId();
        return url.replace("/member/payment-result", "/receptionist/members/" + memberId + "/payment-result");
    }

    private boolean validResult(Map<String, String> params) {
        return matches(params.get("vnp_ResponseCode"), "\\d{2}")
                && matches(params.get("vnp_TransactionStatus"), "\\d{2}")
                && matches(params.get("vnp_TransactionNo"), "\\d{1,15}")
                && (params.get("vnp_PayDate") == null || params.get("vnp_PayDate").isEmpty()
                    || matches(params.get("vnp_PayDate"), "\\d{14}"));
    }

    private boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    private Integer paymentId(Map<String, String> params) {
        String ref = params.get("vnp_TxnRef");
        if (!matches(ref, "[1-9]\\d{0,9}")) return null;
        try { return Integer.valueOf(ref); } catch (NumberFormatException e) { return null; }
    }

    private boolean validAmount(Map<String, String> params, Payment payment) {
        String value = params.get("vnp_Amount");
        return matches(value, "\\d{1,12}")
                && new BigInteger(value).equals(payment.getAmount().movePointRight(2).toBigIntegerExact());
    }

    private String subscriptionDisplayStatus(Payment payment) {
        return switch (payment.getSubscription().getStatus()) {
            case "Active" -> "Đang diễn ra";
            case "Expired" -> "Đã hoàn thành";
            case "Pending" -> "Paid".equals(payment.getStatus()) && !payment.isRequiresReview() ? "Chờ bắt đầu" : "Chờ thanh toán";
            default -> "Đã hủy";
        };
    }

    private LocalDateTime now() { return LocalDateTime.now(paymentClock).withNano(0); }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thanh toán của bạn");
    }
    private Map<String, String> response(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }
    private String displayStatus(String status) {
        return switch (status) {
            case "Pending" -> "Chờ thanh toán";
            case "Booked" -> "Đang diễn ra";
            case "Completed" -> "Đã hoàn thành";
            default -> "Đã hủy";
        };
    }
}
