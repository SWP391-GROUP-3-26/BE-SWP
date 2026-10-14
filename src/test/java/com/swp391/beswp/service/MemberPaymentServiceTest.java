package com.swp391.beswp.service;

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

class MemberPaymentServiceTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ClassRepository classes = mock(ClassRepository.class);
    private final ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    private final MemberClassBookingService memberBookings = mock(MemberClassBookingService.class);
    private final VnpayService vnpay = VnpayServiceTest.configured();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T01:00:00Z"), VnpayService.OFFSET);
    private final ClassReservationService reservations = new ClassReservationService(bookings, payments);
    private final MemberPaymentService service = new MemberPaymentService(payments, classes, bookings, reservations, memberBookings, vnpay, clock);
    private ClassEntity classEntity;
    private ClassBooking booking;
    private Payment payment;
    private User member;

    @BeforeEach
    void setUp() {
        member = new User(); member.setId(1);
        classEntity = ClassEntity.builder().id(10).status("Active").maxCapacity(1).build();
        ClassSession session = ClassSession.builder().id(20).classEntity(classEntity).status("Scheduled")
                .date(LocalDate.of(2026, 10, 14)).startTime(LocalTime.of(8, 0)).build();
        booking = ClassBooking.builder().id(42).session(session).user(member).status("Pending")
                .paymentExpiresAt(LocalDateTime.now(clock).plusMinutes(15)).build();
        payment = Payment.builder().id(105).booking(booking).method("Card").paymentType("Class_Booking")
                .amount(new BigDecimal("100000.00")).status("Pending").expiresAt(booking.getPaymentExpiresAt()).build();
        when(payments.findVnpayClassId(105)).thenReturn(Optional.of(10));
        when(classes.findByIdForUpdate(10)).thenReturn(Optional.of(classEntity));
        when(payments.findById(105)).thenReturn(Optional.of(payment));
        when(payments.findAllByBooking_IdOrderByIdDesc(42)).thenReturn(List.of(payment));
        when(bookings.countReservedMembersByClassId(eq(10), any())).thenReturn(1L);
        when(bookings.countReservedMembersBySessionId(eq(20), any())).thenReturn(1L);
        when(memberBookings.findMemberUser("1")).thenReturn(member);
    }

    private Map<String, String> callback(String responseCode, String transactionStatus) {
        Map<String, String> params = new HashMap<>(Map.of("vnp_TmnCode", "TESTCODE", "vnp_TxnRef", "105",
                "vnp_Amount", "10000000", "vnp_ResponseCode", responseCode, "vnp_TransactionStatus", transactionStatus,
                "vnp_TransactionNo", "123456", "vnp_PayDate", "20261013080200"));
        return signed(params);
    }
    private Map<String, String> signed(Map<String, String> params) {
        params.put("vnp_SecureHash", vnpay.sign(params)); return params;
    }

    @Test
    void successConfirmsExactlyOnceAndLocksBeforeLoadingPayment() {
        Map<String, String> params = callback("00", "00");
        assertEquals("00", service.processIpn(params).get("RspCode"));
        assertEquals("Paid", payment.getStatus()); assertEquals("Booked", booking.getStatus());
        assertFalse(payment.isRequiresReview()); assertNull(booking.getPaymentExpiresAt());
        assertEquals("123456", payment.getVnpTransactionNo());
        assertEquals("02", service.processIpn(params).get("RspCode"));
        verify(bookings, times(1)).findExpiredByClassId(eq(10), any());
        var order = inOrder(classes, payments);
        order.verify(payments).findVnpayClassId(105);
        order.verify(classes).findByIdForUpdate(10);
        order.verify(payments).findById(105);
    }

    @Test
    void eitherFailedResultCodeReleasesReservation() {
        assertEquals("00", service.processIpn(callback("00", "02")).get("RspCode"));
        assertEquals("Failed", payment.getStatus()); assertEquals("Cancelled", booking.getStatus());
        assertEquals("02", service.processIpn(callback("00", "02")).get("RspCode"));
    }

    @Test
    void failedResponseCodeEvenWithTransaction00IsNotSuccess() {
        service.processIpn(callback("24", "00"));
        assertEquals("Failed", payment.getStatus()); assertEquals("Cancelled", booking.getStatus());
    }

    @Test
    void badSignatureAmountMerchantOrResultDoesNotChangeData() {
        var params = callback("00", "00"); params.put("vnp_Amount", "1");
        assertEquals("97", service.processIpn(params).get("RspCode"));
        assertEquals("04", service.processIpn(signed(params)).get("RspCode"));
        params.put("vnp_TmnCode", "OTHER");
        assertEquals("01", service.processIpn(signed(params)).get("RspCode"));
        params = callback("00", "00"); params.remove("vnp_TransactionStatus");
        assertEquals("99", service.processIpn(signed(params)).get("RspCode"));
        assertEquals("Pending", payment.getStatus()); assertEquals("Pending", booking.getStatus());
        assertNull(payment.getVnpTransactionNo());
        verify(bookings, never()).findExpiredByClassId(any(), any());
    }

    @Test
    void expiredPaymentReadReleasesSeatAndLateSuccessIsRetainedForReview() {
        payment.setExpiresAt(LocalDateTime.now(clock)); booking.setPaymentExpiresAt(payment.getExpiresAt());
        when(bookings.findExpiredByClassId(eq(10), any())).thenAnswer(i ->
                "Pending".equals(booking.getStatus()) ? List.of(booking) : List.of());
        var response = service.getPayment("1", 105);
        assertEquals("Failed", response.getPaymentStatus()); assertEquals("Đã hủy", response.getBookingStatus());
        assertEquals(VnpayService.OFFSET, response.getExpiresAt().getOffset());
        assertEquals("00", service.processIpn(callback("00", "00")).get("RspCode"));
        assertEquals("Paid", payment.getStatus()); assertEquals("Cancelled", booking.getStatus());
        assertTrue(payment.isRequiresReview()); assertEquals("123456", payment.getVnpTransactionNo());
        assertEquals("02", service.processIpn(callback("00", "00")).get("RspCode"));
    }

    @Test
    void oldAttemptCannotActivateOrCancelNewReservation() {
        payment.setStatus("Failed");
        Payment next = Payment.builder().id(106).booking(booking).status("Pending").build();
        when(payments.findAllByBooking_IdOrderByIdDesc(42)).thenReturn(List.of(next, payment));
        service.processIpn(callback("00", "00"));
        assertEquals("Paid", payment.getStatus()); assertTrue(payment.isRequiresReview());
        assertEquals("Pending", booking.getStatus()); assertEquals("Pending", next.getStatus());
    }

    @Test
    void cancelledBookingNeverReactivated() {
        booking.setStatus("Cancelled");
        service.processIpn(callback("00", "00"));
        assertTrue(payment.isRequiresReview()); assertEquals("Cancelled", booking.getStatus());
    }

    @Test
    void fullCapacityRequiresReviewInsteadOfActivating() {
        when(bookings.countReservedMembersBySessionId(eq(20), any())).thenReturn(2L);
        service.processIpn(callback("00", "00"));
        assertTrue(payment.isRequiresReview()); assertEquals("Cancelled", booking.getStatus());
    }

    @Test
    void closedClassRecordsMoneyButNeverConfirmsBooking() {
        classEntity.setStatus("Closed");
        service.processIpn(callback("00", "00"));
        assertEquals("Paid", payment.getStatus()); assertTrue(payment.isRequiresReview());
        assertEquals("Cancelled", booking.getStatus());
    }

    @Test
    void unknownPaymentAndMalformedReferenceAreRejectedBeforeAnyMutation() {
        var params = callback("00", "00"); params.put("vnp_TxnRef", "999");
        assertEquals("01", service.processIpn(signed(params)).get("RspCode"));
        params.put("vnp_TxnRef", "9999999999");
        assertEquals("01", service.processIpn(signed(params)).get("RspCode"));
        assertEquals("Pending", payment.getStatus()); assertEquals("Pending", booking.getStatus());
        verify(classes, never()).findByIdForUpdate(any());
    }

    @Test
    void memberCannotReadAnotherMembersPayment() {
        User other = new User(); other.setId(2);
        when(memberBookings.findMemberUser("2")).thenReturn(other);
        var error = assertThrows(ResponseStatusException.class, () -> service.getPayment("2", 105));
        assertEquals(404, error.getStatusCode().value());
        verify(bookings, never()).findExpiredByClassId(any(), any());
    }

    @Test
    void validReturnOnlyRedirectsAndNeverChangesPaymentOrBooking() {
        assertEquals("http://localhost:5173/member/payment-result?paymentId=105", service.returnUrl(callback("00", "00")));
        assertEquals("Pending", payment.getStatus()); assertEquals("Pending", booking.getStatus());
        verify(classes, never()).findByIdForUpdate(any());
        verify(bookings, never()).findExpiredByClassId(any(), any());
        var invalid = callback("00", "00"); invalid.put("vnp_Amount", "0");
        assertThrows(ResponseStatusException.class, () -> service.returnUrl(invalid));
    }

    @Test
    void expiredCourseCannotConfirmPaymentEvenWhenPaymentDeadlineIsStillFuture() {
        classEntity.setDate(LocalDate.of(2026, 10, 6)); classEntity.setEndDate(LocalDate.of(2026, 10, 12));
        classEntity.setDurationWeeks(1); classEntity.setDaysOfWeek("T3, T5");
        assertThrows(ResponseStatusException.class, () -> service.returnUrl(callback("00", "00")));
        var read = service.getPayment("1", 105);
        assertEquals("Failed", read.getPaymentStatus()); assertEquals("Đã hủy", read.getBookingStatus());
        assertEquals("00", service.processIpn(callback("00", "00")).get("RspCode"));
        assertEquals("Paid", payment.getStatus()); assertTrue(payment.isRequiresReview());
        assertEquals("Cancelled", booking.getStatus());
    }

    @Test
    void futureSessionBeyondEndDateOrOnWrongWeekdayCannotBeConfirmed() {
        classEntity.setDate(LocalDate.of(2026, 10, 13)); classEntity.setEndDate(LocalDate.of(2026, 10, 18));
        classEntity.setDurationWeeks(1); classEntity.setDaysOfWeek("T3, T5");
        booking.getSession().setDate(LocalDate.of(2026, 10, 20));
        service.processIpn(callback("00", "00"));
        assertTrue(payment.isRequiresReview()); assertEquals("Cancelled", booking.getStatus());
        payment.setVnpResponseCode(null); payment.setStatus("Pending"); booking.setStatus("Pending");
        booking.getSession().setDate(LocalDate.of(2026, 10, 14));
        service.processIpn(callback("00", "00"));
        assertTrue(payment.isRequiresReview()); assertEquals("Cancelled", booking.getStatus());
    }
}
