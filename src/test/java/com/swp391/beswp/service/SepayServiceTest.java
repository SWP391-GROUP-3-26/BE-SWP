package com.swp391.beswp.service;

import com.swp391.beswp.config.SepayConfig;
import com.swp391.beswp.dto.SepayWebhookRequest;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SepayServiceTest {
    final PaymentRepository payments = mock(PaymentRepository.class);
    final ClassRepository classes = mock(ClassRepository.class);
    final ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-10-16T01:00:00Z"), VnpayService.OFFSET);
    final SepayConfig config = configured();
    final ClassReservationService reservations = new ClassReservationService(bookings, payments);
    final SepayService service = new SepayService(config, payments, classes, bookings, reservations, clock);
    final String auth = "Apikey " + config.getWebhookApiKey();
    Payment payment;
    ClassBooking booking;
    ClassEntity course;

    static SepayConfig configured() {
        var value = new SepayConfig();
        value.setEnabled(true); value.setTestMode(true); value.setBankCode("MBBank");
        value.setAccountNumber("0123456789"); value.setAccountName("NGUYEN VAN TEST");
        value.setWebhookApiKey("unit-test-only-key-at-least-32-characters");
        return value;
    }

    @BeforeEach
    void setup() {
        course = ClassEntity.builder().id(10).status("Active").maxCapacity(1).build();
        var session = ClassSession.builder().id(20).classEntity(course).status("Scheduled")
                .date(LocalDate.of(2026, 10, 17)).startTime(LocalTime.of(8, 0)).build();
        booking = ClassBooking.builder().id(42).session(session).status("Pending")
                .paymentExpiresAt(LocalDateTime.now(clock).plusMinutes(15)).build();
        payment = Payment.builder().id(105).booking(booking).method("E_Wallet").paymentType("Class_Booking")
                .amount(new BigDecimal("100000.00")).status("Pending").expiresAt(booking.getPaymentExpiresAt()).build();
        when(payments.findSepayClassId(105)).thenReturn(Optional.of(10));
        when(classes.findByIdForUpdate(10)).thenReturn(Optional.of(course));
        when(payments.findById(105)).thenReturn(Optional.of(payment));
        when(payments.findAllByBooking_IdOrderByIdDesc(42)).thenReturn(List.of(payment));
        when(bookings.countReservedMembersBySessionId(eq(20), any())).thenReturn(1L);
    }

    SepayWebhookRequest transaction(Long id, String account, String code, String type, String amount) {
        return new SepayWebhookRequest(id, "MBBank", account, code, "thanh toan SWP105", type, new BigDecimal(amount));
    }
    SepayWebhookRequest valid() { return transaction(12345L, config.getAccountNumber(), "SWP105", "in", "100000"); }

    @Test
    void qrContainsDatabaseAmountAndPaymentCodeWithoutSecrets() {
        var qr = service.qrPayment(payment);
        assertEquals("SWP105", qr.paymentCode()); assertTrue(qr.testMode());
        assertTrue(qr.qrUrl().contains("amount=100000&des=SWP105"));
        assertFalse(qr.qrUrl().contains(config.getWebhookApiKey()));
        payment.setStatus("Paid"); assertNull(service.qrPayment(payment));
        config.setEnabled(false); assertThrows(ResponseStatusException.class, service::requireConfigured);
    }

    @Test
    void confirmsWholeRegistrationOnlyOnceAndLocksBeforeLoading() {
        var second = ClassBooking.builder().id(43).session(booking.getSession()).status("Pending")
                .registrationCode("registration").paymentExpiresAt(booking.getPaymentExpiresAt()).build();
        booking.setRegistrationCode("registration");
        when(bookings.findByRegistrationCodeOrderByIdAsc("registration")).thenReturn(List.of(booking, second));
        service.processWebhook(auth, valid());
        assertEquals("Paid", payment.getStatus()); assertFalse(payment.isRequiresReview());
        assertEquals(12345L, payment.getSepayTransactionId());
        assertEquals("Booked", booking.getStatus()); assertEquals("Booked", second.getStatus());
        assertNull(booking.getPaymentExpiresAt()); assertNull(second.getPaymentExpiresAt());
        service.processWebhook(auth, valid());
        verify(bookings, times(1)).findExpiredByClassId(eq(10), any());
        var order = inOrder(classes, payments);
        order.verify(classes).findByIdForUpdate(10); order.verify(payments).findById(105);
    }

    @Test
    void wrongAuthenticationAndMalformedTransactionsCannotChangeState() {
        for (String authorization : Arrays.asList(null, "Bearer invalid", "Apikey wrong")) {
            assertEquals(401, assertThrows(ResponseStatusException.class,
                    () -> service.processWebhook(authorization, valid())).getStatusCode().value());
        }
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.processWebhook(auth, transaction(null, "0123456789", "SWP105", "in", "100000"))).getStatusCode().value());
        verifyNoInteractions(payments, classes, bookings);
        assertEquals("Pending", payment.getStatus());
    }

    @Test
    void wrongAccountDirectionCodeBankOrAmountNeverConfirms() {
        for (var event : List.of(transaction(1L, "wrong", "SWP105", "in", "100000"),
                transaction(2L, "0123456789", "SWP105", "out", "100000"),
                transaction(3L, "0123456789", "SWP999", "in", "100000"),
                transaction(4L, "0123456789", "SWP105", "in", "99999"),
                transaction(5L, "0123456789", "SWP105", "in", "100001"),
                transaction(6L, "0123456789", "SWP105 SWP106", "in", "100000"),
                transaction(7L, "0123456789", "SWP9999999999", "in", "100000"),
                new SepayWebhookRequest(8L, "OtherBank", "0123456789", "SWP105", "SWP105", "in", new BigDecimal("100000")))) {
            service.processWebhook(auth, event);
            assertEquals("Pending", payment.getStatus()); assertEquals("Pending", booking.getStatus());
            assertNull(payment.getSepayTransactionId());
        }
    }

    @Test
    void duplicateTransactionCannotPayAnotherOrderAndMissingCodeFallsBackToContent() {
        when(payments.existsBySepayTransactionId(12345L)).thenReturn(true);
        service.processWebhook(auth, valid());
        assertEquals("Pending", payment.getStatus()); verifyNoInteractions(classes);
        when(payments.existsBySepayTransactionId(12345L)).thenReturn(false);
        service.processWebhook(auth, transaction(12345L, "0123456789", null, "in", "100000"));
        assertEquals("Paid", payment.getStatus());
    }

    @Test
    void expiredCancelledClosedFullOrSupersededRegistrationRecordsMoneyForReview() {
        for (String condition : List.of("expired", "cancelled", "closed", "full", "superseded")) {
            setup();
            switch (condition) {
                case "expired" -> { payment.setExpiresAt(LocalDateTime.now(clock)); booking.setPaymentExpiresAt(payment.getExpiresAt()); }
                case "cancelled" -> booking.setStatus("Cancelled");
                case "closed" -> course.setStatus("Closed");
                case "full" -> when(bookings.countReservedMembersBySessionId(eq(20), any())).thenReturn(2L);
                case "superseded" -> when(payments.findAllByBooking_IdOrderByIdDesc(42)).thenReturn(List.of(Payment.builder().id(106).build(), payment));
            }
            service.processWebhook(auth, valid());
            assertEquals("Paid", payment.getStatus(), condition); assertTrue(payment.isRequiresReview(), condition);
            assertEquals("Cancelled", booking.getStatus(), condition);
            when(bookings.countReservedMembersBySessionId(eq(20), any())).thenReturn(1L);
        }
    }
}
