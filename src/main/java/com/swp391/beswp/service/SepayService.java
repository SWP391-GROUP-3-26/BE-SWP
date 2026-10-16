package com.swp391.beswp.service;

import com.swp391.beswp.config.SepayConfig;
import com.swp391.beswp.dto.SepayQrPayment;
import com.swp391.beswp.dto.SepayWebhookRequest;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SepayService {
    private static final Pattern PAYMENT_CODE = Pattern.compile("(?i)(?<![A-Z0-9])SWP([1-9]\\d{0,9})(?![A-Z0-9])");
    private final SepayConfig config;
    private final PaymentRepository payments;
    private final ClassRepository classes;
    private final ClassBookingRepository bookings;
    private final ClassReservationService reservations;
    private final Clock paymentClock;

    public void requireConfigured() {
        if (!config.isEnabled() || config.getBankCode() == null || !config.getBankCode().matches("[A-Za-z0-9]{2,30}")
                || config.getAccountNumber() == null || !config.getAccountNumber().matches("[A-Za-z0-9]{5,40}")
                || config.getAccountName() == null || config.getAccountName().isBlank()
                || config.getWebhookApiKey() == null || config.getWebhookApiKey().length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Thanh toán QR SePay chưa được cấu hình");
        }
    }

    public SepayQrPayment qrPayment(Payment payment) {
        if (!"E_Wallet".equals(payment.getMethod()) || !"Pending".equals(payment.getStatus())
                || payment.getExpiresAt() == null) return null;
        requireConfigured();
        String code = "SWP" + payment.getId();
        String url = "https://vietqr.app/img?acc=" + encode(config.getAccountNumber())
                + "&bank=" + encode(config.getBankCode()) + "&amount=" + payment.getAmount().toBigIntegerExact()
                + "&des=" + code + "&template=compact";
        return new SepayQrPayment(url, config.getBankCode(), config.getAccountNumber(), config.getAccountName(), code, config.isTestMode());
    }

    @Transactional
    public void processWebhook(String authorization, SepayWebhookRequest request) {
        requireConfigured();
        String expected = "Apikey " + config.getWebhookApiKey();
        if (authorization == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook SePay không hợp lệ");
        }
        if (request == null || request.id() == null || request.id() <= 0 || request.transferAmount() == null
                || request.transferAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giao dịch SePay không hợp lệ");
        }
        if (!"in".equals(request.transferType()) || !config.getAccountNumber().equals(request.accountNumber())
                || !config.getBankCode().equalsIgnoreCase(request.gateway())) return;
        Integer paymentId = paymentId(request);
        if (paymentId == null || payments.existsBySepayTransactionId(request.id())) return;
        Integer classId = payments.findSepayClassId(paymentId).orElse(null);
        if (classId == null) return;
        var classEntity = classes.findByIdForUpdate(classId).orElseThrow();
        Payment payment = payments.findById(paymentId).orElseThrow();
        // Follow the existing class lock order so cancellation, expiry and callbacks cannot race for seats.
        if (payment.getSepayTransactionId() != null || payments.existsBySepayTransactionId(request.id())) return;
        if (payment.getAmount().compareTo(request.transferAmount()) != 0) {
            log.warn("SePay amount mismatch: transaction {}, payment {}", request.id(), paymentId);
            return;
        }
        LocalDateTime now = LocalDateTime.now(paymentClock).withNano(0);
        reservations.expirePending(classId, now);
        var booking = payment.getBooking();
        var registration = reservations.registrationBookings(booking);
        var attempts = payments.findAllByBooking_IdOrderByIdDesc(booking.getId());
        boolean canConfirm = !attempts.isEmpty() && attempts.getFirst().getId().equals(paymentId)
                && "Pending".equals(payment.getStatus()) && payment.getExpiresAt().isAfter(now)
                && !registration.isEmpty() && registration.stream().allMatch(item ->
                "Pending".equals(item.getStatus()) && item.getPaymentExpiresAt() != null
                        && item.getPaymentExpiresAt().isAfter(now)
                        && ClassSchedule.bookable(classEntity, item.getSession(), now)
                        && bookings.countReservedMembersBySessionId(item.getSession().getId(), now) <= classEntity.getMaxCapacity());
        payment.setSepayTransactionId(request.id());
        payment.setPaymentDate(now);
        payment.setStatus("Paid");
        payment.setRequiresReview(!canConfirm);
        if (canConfirm) {
            registration.forEach(item -> { item.setStatus("Booked"); item.setPaymentExpiresAt(null); });
        } else {
            reservations.cancelPendingRegistration(booking);
        }
    }

    private Integer paymentId(SepayWebhookRequest request) {
        String value = request.code() == null || request.code().isBlank() ? request.content() : request.code();
        if (value == null || value.length() > 2000) return null;
        var matcher = PAYMENT_CODE.matcher(value);
        if (!matcher.find()) return null;
        String id = matcher.group(1);
        if (matcher.find()) return null;
        try { return Integer.valueOf(id); } catch (NumberFormatException ex) { return null; }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
