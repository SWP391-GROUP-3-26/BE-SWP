package com.swp391.beswp.service;

import com.swp391.beswp.dto.RefundDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefundServiceTest {
    RefundRequestRepository refunds = mock(RefundRequestRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    ClassRepository classes = mock(ClassRepository.class);
    ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    UserRepository users = mock(UserRepository.class);
    ActivityLogRepository logs = mock(ActivityLogRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    RefundService service = new RefundService(refunds, payments, classes, bookings, users, logs, clock);
    User member, manager, cashier;
    Payment payment;
    List<ClassBooking> registration;
    RefundRequest request;

    @BeforeEach void setup() {
        member = person(1, "Member"); manager = person(2, "Center Manager"); cashier = person(3, "Receptionist");
        ClassEntity c = ClassEntity.builder().id(10).name("Bơi cơ bản").build();
        LocalDate today = LocalDate.of(2026, 10, 30);
        registration = new ArrayList<>();
        for (int i = 0; i < 3; i++) registration.add(ClassBooking.builder().id(20 + i).user(member).status("Booked").paymentMethod("DIRECT")
                .session(ClassSession.builder().id(30 + i).classEntity(c).date(today.plusDays(i == 0 ? -1 : i)).startTime(LocalTime.of(9, 0)).build()).build());
        payment = Payment.builder().id(50).booking(registration.getFirst()).status("Paid").amount(new BigDecimal("1000000"))
                .discountAmount(new BigDecimal("200000")).itemNameAtPayment("Tên lúc mua").build();
        when(refunds.saveAndFlush(any())).thenAnswer(call -> { request = call.getArgument(0); request.setId(60); return request; });
    }
    User person(int id, String roleName) {
        Role role = new Role(); role.setRoleName(roleName);
        User u = new User(); u.setId(id); u.setFullName(roleName); u.setRole(role); u.setStatus("Active");
        when(users.findById(id)).thenReturn(Optional.of(u)); return u;
    }
    Authentication auth(User u) { return new UsernamePasswordAuthenticationToken(u.getId().toString(), "", List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().getRoleName()))); }
    void create() {
        service.createForCancellation(payment, registration, Set.of(21, 22), member);
        when(refunds.findByIdForUpdate(60)).thenReturn(Optional.of(request));
        when(refunds.findById(60)).thenReturn(Optional.of(request));
    }
    void error(int status, Runnable action) { assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value()); }

    @Test void proratesNetPaidAmountAndRoundsDownToDong() {
        assertEquals(new BigDecimal("666666"), RefundService.estimate(new BigDecimal("1000000"), 3, 2));
        assertEquals(new BigDecimal("1000000"), RefundService.estimate(new BigDecimal("1000000"), 3, 3));
        assertEquals(BigDecimal.ZERO, RefundService.estimate(new BigDecimal("1000000"), 3, 0));
        assertThrows(IllegalArgumentException.class, () -> RefundService.estimate(BigDecimal.TEN, 0, 0));
    }
    @Test void previewOnlyRefundsUnstartedBookingsAndUsesNetNotOriginalPrice() {
        CancellationPreview result = service.preview(20, registration, List.of(payment));
        assertEquals(2, result.cancelSessions()); assertEquals(new BigDecimal("500000"), result.estimatedRefund());
        assertEquals(new BigDecimal("1000000"), result.paidAmount());
    }
    @Test void legacyMixedCancellationRefundsOnlyDirectlyPaidRows() {
        registration.getFirst().setPaymentMethod("PACKAGE");
        registration.get(1).setPaymentMethod("PACKAGE");
        CancellationPreview preview = service.preview(20, registration, List.of(payment));
        assertEquals(new BigDecimal("500000"), preview.estimatedRefund());
        service.createForCancellation(payment, registration, Set.of(21, 22), member);
        assertEquals(1, request.getTotalSessions()); assertEquals(1, request.getCancelledSessions());
        assertEquals(1, request.getSessions().size());
        assertEquals(32, request.getSessions().getFirst().getSessionId());
        assertEquals(new BigDecimal("500000"), request.getEstimatedAmount());
    }
    @Test void snapshotsSessionsAndCreatesOnlyOneRequestPerPayment() {
        create(); assertEquals("Tên lúc mua", request.getItemName()); assertTrue(payment.isRequiresReview());
        assertEquals(3, request.getSessions().size()); assertEquals(2, request.getSessions().stream().filter(RefundSessionSnapshot::isRefundable).count());
        when(refunds.findByPayment_Id(50)).thenReturn(Optional.of(request));
        service.createForCancellation(payment, registration, Set.of(21, 22), member);
        verify(refunds, times(1)).saveAndFlush(any());
    }
    @Test void pendingPaymentsCannotCreateCashRefunds() {
        payment.setStatus("Pending");
        error(409, () -> service.createForCancellation(payment, registration, Set.of(21), member));
    }
    @Test void reviewerMustHaveActiveManagerRole() {
        create(); error(403, () -> service.review(auth(member), 60, new Review("APPROVED", BigDecimal.TEN, "", 0L)));
        manager.setStatus("Locked"); error(403, () -> service.review(auth(manager), 60, new Review("APPROVED", BigDecimal.TEN, "", 0L)));
    }
    @Test void legacyApprovalCannotExceedPaymentAndAdjustmentRequiresReason() {
        create(); request.setRefundPolicy(null); error(400, () -> service.review(auth(manager), 60, new Review("APPROVED", new BigDecimal("1000001"), "", 0L)));
        error(400, () -> service.review(auth(manager), 60, new Review("APPROVED", new BigDecimal("700000"), "", 0L)));
        assertEquals(new BigDecimal("700000"), service.review(auth(manager), 60, new Review("APPROVED", new BigDecimal("700000"), "Điều chỉnh theo chính sách", 0L)).approvedAmount());
    }
    @Test void rejectionNeedsReasonAndClosesReviewFlag() {
        create(); error(400, () -> service.review(auth(manager), 60, new Review("REJECTED", null, " ", 0L)));
        assertEquals("REJECTED", service.review(auth(manager), 60, new Review("REJECTED", null, "Không đủ điều kiện", 0L)).status());
        assertFalse(payment.isRequiresReview());
        error(409, () -> service.review(auth(manager), 60, new Review("APPROVED", BigDecimal.TEN, "", 0L)));
    }
    @Test void onlyOwnerCanGenerateCounterCodeAndBankRefundsAreRejected() {
        create(); User other = person(4, "Member");
        error(404, () -> service.recipient(auth(other), 60, new Recipient("CASH", 0L)));
        service.review(auth(manager), 60, new Review("APPROVED", request.getEstimatedAmount(), "", 0L));
        error(400, () -> service.recipient(auth(member), 60, new Recipient("BANK", 0L)));
        RefundView result = service.recipient(auth(member), 60, new Recipient("CASH", 0L));
        assertTrue(result.pickupCode().matches("HT-(?:[A-HJ-NP-Z2-9]{4}-){3}[A-HJ-NP-Z2-9]{4}"));
        assertEquals(result.pickupCode(), service.recipient(auth(member), 60, new Recipient("CASH", 0L)).pickupCode());
        assertNull(service.detail(auth(cashier), 60, "Receptionist").pickupCode());
        assertNull(service.detail(auth(manager), 60, "Center Manager").pickupCode());
        assertNull(service.view(request, false).pickupCode());
    }
    @Test void payoutRequiresApprovalRecipientAndReceiptAndCannotRepeat() {
        create(); error(409, () -> service.payout(auth(cashier), 60, new Payout("R1", "", 0L)));
        service.review(auth(manager), 60, new Review("APPROVED", request.getEstimatedAmount(), "", 0L));
        error(409, () -> service.payout(auth(cashier), 60, new Payout("R1", "", 0L)));
        service.recipient(auth(member), 60, new Recipient("CASH", 0L));
        error(400, () -> service.payout(auth(cashier), 60, new Payout(" ", "", 0L)));
        error(400, () -> service.payout(auth(cashier), 60, new Payout("WRONG", "", 0L)));
        assertEquals("APPROVED", request.getStatus()); assertNull(request.getPaidAt());
        RefundView paid = service.payout(auth(cashier), 60, new Payout(request.getPickupCode().toLowerCase(Locale.ROOT).replace("-", ""), "Đã giao tiền", 0L));
        assertEquals("PAID", paid.status()); assertEquals("Paid", payment.getStatus()); assertFalse(payment.isRequiresReview());
        assertNull(paid.pickupCode()); assertEquals("CASH-HT-60", paid.payoutReference());
        error(409, () -> service.payout(auth(cashier), 60, new Payout("R2", "", 0L)));
    }
    @Test void staleChangesCannotOverwriteApprovalOrRecipient() {
        create(); request.setVersion(2);
        error(409, () -> service.review(auth(manager), 60, new Review("APPROVED", BigDecimal.TEN, "", 0L)));
        error(409, () -> service.recipient(auth(member), 60, new Recipient("CASH", 0L)));
    }
    @Test void readingOldNoticeDoesNotHideLaterApproval() {
        create(); service.markRead(auth(member), 60, 1);
        service.review(auth(manager), 60, new Review("APPROVED", request.getEstimatedAmount(), "", 0L));
        assertTrue(service.markRead(auth(member), 60, 1).unread());
        assertFalse(service.markRead(auth(member), 60, 2).unread());
    }

    @Test void approvedCodeStaysStableAndBankCannotReplaceIt() {
        create();
        service.review(auth(manager), 60, new Review("APPROVED", request.getEstimatedAmount(), "", 0L));
        String code = service.recipient(auth(member), 60, new Recipient("CASH", 0L)).pickupCode();
        assertEquals(code, service.recipient(auth(member), 60, new Recipient("CASH", 0L)).pickupCode());
        error(400, () -> service.recipient(auth(member), 60, new Recipient("BANK", 0L)));
    }

    @Test void generationRetriesExistingCodesAndMigratesAnUnpaidBankRequestOnlyWhenMemberChoosesCash() {
        create(); request.setPayoutMethod("BANK"); request.setAccountNumber("123456");
        service.review(auth(manager), 60, new Review("APPROVED", request.getEstimatedAmount(), "", 0L));
        when(refunds.existsByPickupCode(anyString())).thenReturn(true, false);
        var result = service.recipient(auth(member), 60, new Recipient("CASH", 0L));
        assertNotNull(result.pickupCode()); assertNull(result.accountNumber());
        verify(refunds, times(2)).existsByPickupCode(anyString());
    }

    @Test void unapprovedRefundsCannotGenerateOrExposeEvenPreviouslyCreatedCodes() {
        create();
        for (String status : List.of("PENDING", "REJECTED", "PAID")) {
            request.setStatus(status);
            error(409, () -> service.recipient(auth(member), 60, new Recipient("CASH", 0L)));
            assertNull(request.getPickupCode());
        }
        request.setStatus("PENDING"); request.setPayoutMethod("CASH"); request.setPickupCode("HT-OLD-CODE");
        var result = service.detail(auth(member), 60, "Member");
        assertNull(result.pickupCode()); assertFalse(result.pickupReady());
        verify(refunds, never()).existsByPickupCode(anyString());
        request.setStatus("APPROVED");
        assertEquals("HT-OLD-CODE", service.detail(auth(member), 60, "Member").pickupCode());
    }
}
