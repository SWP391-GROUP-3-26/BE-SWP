package com.swp391.beswp.service;

import com.swp391.beswp.dto.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

// Real creation, registration, payment and listing services share an in-memory repository fixture.
class ClassRegistrationFlowTest {
    final ClassRepository classes = mock(ClassRepository.class);
    final ClassSessionRepository sessions = mock(ClassSessionRepository.class);
    final ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final SubjectRepository subjects = mock(SubjectRepository.class);
    final RoomRepository rooms = mock(RoomRepository.class);
    final MemberSubscriptionRepository subscriptions = mock(MemberSubscriptionRepository.class);
    final MembershipIncludedSportRepository includedSports = mock(MembershipIncludedSportRepository.class);
    final MembershipSportSelectionRepository selections = mock(MembershipSportSelectionRepository.class);
    final List<ClassSession> sessionRows = new ArrayList<>();
    final List<ClassBooking> bookingRows = new ArrayList<>();
    final List<Payment> paymentRows = new ArrayList<>();
    final AtomicReference<Instant> current = new AtomicReference<>(Instant.parse("2026-10-13T00:00:00Z"));
    final Clock clock = mock(Clock.class);
    final VnpayService vnpay = VnpayServiceTest.configured();
    MemberClassBookingService service;
    MemberPaymentService paymentService;
    ClassEntity course;

    @BeforeEach
    void setup() {
        when(clock.getZone()).thenReturn(VnpayService.OFFSET);
        when(clock.instant()).thenAnswer(i -> current.get());
        Role role = new Role(); role.setRoleName("Member");
        User user = new User(); user.setId(1); user.setRole(role); user.setFullName("Member");
        when(users.findById(1)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user));
        Role coachRole = new Role(); coachRole.setRoleName("Coach");
        User coach = new User(); coach.setId(3); coach.setRole(coachRole); coach.setFullName("Coach");
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(coach));
        when(rooms.findByIdForUpdate(1)).thenReturn(Optional.of(Room.builder().id(1).name("Room").maxCapacity(10).build()));
        when(subjects.findById(1)).thenReturn(Optional.of(Subject.builder().id(1).name("Yoga").build()));
        when(classes.save(any())).thenAnswer(i -> { course = i.getArgument(0); course.setId(10); return course; });
        when(sessions.saveAll(any())).thenAnswer(i -> {
            Iterable<ClassSession> rows = i.getArgument(0);
            rows.forEach(s -> { s.setId(20 + sessionRows.size()); sessionRows.add(s); }); return sessionRows;
        });
        new ClassService(classes, subjects, users, rooms, sessions, clock).createClass(CreateClassRequest.builder()
                .name("Yoga 2 weeks").subjectId(1).coachId(3).roomId(1).maxCapacity(10).price(new BigDecimal("100000.00"))
                .daysOfWeek(List.of("T3", "T5")).slotId(1).startTime(LocalTime.of(7, 30)).endTime(LocalTime.of(8, 45))
                .startDate(LocalDate.of(2026, 10, 13)).durationWeeks(2).build());
        when(classes.findByIdForUpdate(10)).thenAnswer(i -> Optional.of(course));
        when(classes.findById(10)).thenAnswer(i -> Optional.of(course));
        when(sessions.findUpcomingScheduledSessions(eq(10), any())).thenReturn(sessionRows);
        when(bookings.save(any())).thenAnswer(i -> {
            ClassBooking b = i.getArgument(0); b.setId(40 + bookingRows.size()); bookingRows.add(b); return b;
        });
        when(bookings.findByRegistrationCodeOrderByIdAsc(anyString())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> b.getRegistrationCode().equals(i.getArgument(0))).toList());
        when(bookings.findMyBookings(eq(1), isNull(), isNull())).thenAnswer(i -> List.copyOf(bookingRows));
        when(bookings.existsActiveBookingByUserIdAndClassId(eq(1), eq(10), any())).thenAnswer(i -> bookingRows.stream()
                .anyMatch(b -> "Booked".equals(b.getStatus()) || ("Pending".equals(b.getStatus()) && b.getPaymentExpiresAt().isAfter(i.getArgument(2)))));
        when(bookings.findExpiredByClassId(eq(10), any())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> "Pending".equals(b.getStatus()) && !b.getPaymentExpiresAt().isAfter(i.getArgument(1))).toList());
        when(bookings.countReservedMembersByClassId(eq(10), any())).thenReturn(1L);
        when(bookings.countReservedMembersBySessionId(anyInt(), any())).thenReturn(1L);
        when(payments.save(any())).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(100 + paymentRows.size()); paymentRows.add(p); return p;
        });
        when(payments.findAllByBooking_IdOrderByIdDesc(anyInt())).thenAnswer(i -> paymentRows.stream()
                .filter(p -> p.getBooking().getId().equals(i.getArgument(0))).sorted(Comparator.comparing(Payment::getId).reversed()).toList());
        when(payments.findVnpayClassId(anyInt())).thenAnswer(i -> paymentRows.stream()
                .filter(p -> p.getId().equals(i.getArgument(0))).findFirst().map(p -> 10));
        when(payments.findOnlineClassId(anyInt())).thenAnswer(i -> paymentRows.stream()
                .filter(p -> p.getId().equals(i.getArgument(0))).findFirst().map(p -> 10));
        when(payments.findById(anyInt())).thenAnswer(i -> paymentRows.stream().filter(p -> p.getId().equals(i.getArgument(0))).findFirst());
        when(bookings.findOwnedClassId(anyInt(), eq(1))).thenReturn(Optional.of(10));
        when(bookings.findByIdAndUser_Id(anyInt(), eq(1))).thenAnswer(i -> bookingRows.stream()
                .filter(b -> b.getId().equals(i.getArgument(0))).findFirst());
        ClassReservationService reservations = new ClassReservationService(bookings, payments);
        service = new MemberClassBookingService(classes, sessions, bookings, subscriptions, includedSports, selections, payments, mock(ActivityLogRepository.class),
                users, subjects, vnpay, mock(SepayService.class), reservations, clock,
                new RefundService(mock(RefundRequestRepository.class), payments, classes, bookings, users, mock(ActivityLogRepository.class), clock),
                mock(CoachAttendanceRepository.class));
        paymentService = new MemberPaymentService(payments, classes, bookings, reservations, service, vnpay, mock(SepayService.class), clock, mock(OnlineSubscriptionPaymentService.class));
    }

    MemberClassBookingResponse register(String method, String channel) {
        return service.registerClass("1", 10, MemberClassBookingRequest.builder().paymentMethod(method).paymentChannel(channel).build(), "127.0.0.1");
    }

    Map<String, String> callback(Payment payment, String code) {
        Map<String, String> params = new HashMap<>(Map.of("vnp_TmnCode", "TESTCODE", "vnp_TxnRef", payment.getId().toString(),
                "vnp_Amount", payment.getAmount().movePointRight(2).toBigIntegerExact().toString(), "vnp_ResponseCode", code,
                "vnp_TransactionStatus", code, "vnp_TransactionNo", "123456", "vnp_PayDate", "20261013070500"));
        params.put("vnp_SecureHash", vnpay.sign(params)); return params;
    }

    MemberSubscription mixedPackage() {
        MemberSubscription sub = MemberSubscription.builder().id(90).status("Active")
                .membershipPackage(MembershipPackage.builder().name("3 lượt").includedClasses(3).build())
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2026, 10, 31)).build();
        when(subscriptions.findCurrentActiveSubscription(eq(1), any())).thenReturn(Optional.of(sub));
        when(includedSports.hasActiveIncludedEntitlement(eq(1), eq(1), any())).thenReturn(true);
        when(bookings.countUsedPackageSessions(eq(1), any(), any())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> "PACKAGE".equals(b.getPaymentMethod()) && List.of("Booked", "Completed").contains(b.getStatus())).count());
        when(bookings.countHeldPackageSessions(eq(1), any(), any(), any())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> "PACKAGE".equals(b.getPaymentMethod()) && "Pending".equals(b.getStatus())
                        && b.getPaymentExpiresAt().isAfter(i.getArgument(3))).count());
        return sub;
    }

    MemberClassBookingResponse mixed(String channel) {
        return service.registerClass("1", 10, MemberClassBookingRequest.builder().paymentMethod("MIXED").paymentChannel(channel)
                .packageSessions(3).paymentAmount(new BigDecimal("100000")).build(), "127.0.0.1");
    }

    @Test void mixedQuoteChargesOnlyMissingSessionsAndHonorsSportAndTerm() {
        var sub = mixedPackage();
        var quote = service.getClassConfirmationDetail("1", 10).getPackageOption();
        assertFalse(quote.isEligible()); assertTrue(quote.isMixedEligible());
        assertEquals(3, quote.getPackageSessions()); assertEquals(1, quote.getPayableSessions());
        assertEquals(new BigDecimal("100000.00"), quote.getAdditionalAmount());
        when(includedSports.hasActiveIncludedEntitlement(eq(1), eq(1), any())).thenReturn(false);
        assertFalse(service.getClassConfirmationDetail("1", 10).getPackageOption().isMixedEligible());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> mixed("Card")).getStatusCode().value());
        when(includedSports.hasActiveIncludedEntitlement(eq(1), eq(1), any())).thenReturn(true);
        sub.setEndDate(LocalDate.of(2026, 10, 18));
        assertFalse(service.getClassConfirmationDetail("1", 10).getPackageOption().isMixedEligible());
        assertThrows(ResponseStatusException.class, () -> mixed("Card"));
        assertTrue(bookingRows.isEmpty()); assertTrue(paymentRows.isEmpty());
    }

    @Test void mixedPaymentHoldsQuotaThenConfirmsExactlyOnceAndRefundsCashPartOnly() {
        mixedPackage();
        var result = mixed("Card");
        assertEquals(new BigDecimal("100000.00"), result.getPaymentAmount());
        assertNotNull(result.getPaymentUrl());
        assertEquals(List.of("PACKAGE", "PACKAGE", "PACKAGE", "DIRECT"), bookingRows.stream().map(ClassBooking::getPaymentMethod).toList());
        assertTrue(bookingRows.stream().allMatch(b -> "Pending".equals(b.getStatus())));
        var info = service.getActiveSubscriptionInfo(users.findById(1).orElseThrow());
        assertEquals(0, info.getUsedSessions()); assertEquals(3, info.getHeldSessions()); assertEquals(0, info.getRemainingSessions());
        var listed = service.getMyClasses("1", "all", null).getClasses().getFirst();
        assertTrue(listed.isCanResumePayment()); assertEquals(3, listed.getHeldPackageSessions());
        assertEquals(result.getPaymentId(), listed.getPaymentId());
        assertEquals(result.getExpiresAt(), listed.getPaymentExpiresAt());
        assertEquals("Gói hội viên + thanh toán phần thiếu", listed.getPaymentMethod());
        var resumed = paymentService.resumePayment("1", result.getPaymentId(), "127.0.0.1");
        assertTrue(resumed.isCanResumePayment()); assertEquals(result.getPaymentUrl(), resumed.getPaymentUrl());
        assertEquals(3, service.getActiveSubscriptionInfo(users.findById(1).orElseThrow()).getHeldSessions());
        assertEquals(1, paymentRows.size()); assertEquals(4, bookingRows.size());
        ClassEntity other = ClassEntity.builder().id(11).status("Active").date(course.getDate()).endDate(course.getEndDate())
                .startTime(course.getStartTime()).subject(course.getSubject()).maxCapacity(10).price(course.getPrice()).build();
        when(classes.findByIdForUpdate(11)).thenReturn(Optional.of(other));
        when(sessions.findUpcomingScheduledSessions(eq(11), any())).thenReturn(sessionRows);
        assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 11,
                MemberClassBookingRequest.builder().paymentMethod("PACKAGE").build(), "127.0.0.1"));
        Payment payment = paymentRows.getFirst();
        var params = callback(payment, "00");
        assertEquals("00", paymentService.processIpn(params).get("RspCode"));
        assertEquals("02", paymentService.processIpn(params).get("RspCode"));
        assertTrue(bookingRows.stream().allMatch(b -> "Booked".equals(b.getStatus())));
        info = service.getActiveSubscriptionInfo(users.findById(1).orElseThrow());
        assertEquals(3, info.getUsedSessions()); assertEquals(0, info.getHeldSessions());
        var preview = service.cancellationPreview("1", bookingRows.getFirst().getId());
        assertEquals(3, preview.packageSessionsReturned()); assertEquals(new BigDecimal("100000"), preview.estimatedRefund());
    }

    @Test void failedMixedPaymentReleasesAllHeldQuota() {
        mixedPackage(); mixed("Card");
        paymentService.processIpn(callback(paymentRows.getFirst(), "24"));
        assertEquals("Failed", paymentRows.getFirst().getStatus());
        assertTrue(bookingRows.stream().allMatch(b -> "Cancelled".equals(b.getStatus())));
        var info = service.getActiveSubscriptionInfo(users.findById(1).orElseThrow());
        assertEquals(3, info.getRemainingSessions()); assertEquals(0, info.getHeldSessions());
    }

    @Test void cancellingPendingMixedPaymentReleasesQuotaWithoutCreatingCashRefund() {
        mixedPackage(); mixed("Card");
        service.cancelClassBooking("1", bookingRows.getFirst().getId());
        assertTrue(bookingRows.stream().allMatch(b -> "Cancelled".equals(b.getStatus())));
        assertEquals("Failed", paymentRows.getFirst().getStatus());
        assertEquals(3, service.getActiveSubscriptionInfo(users.findById(1).orElseThrow()).getRemainingSessions());
        paymentService.processIpn(callback(paymentRows.getFirst(), "00"));
        assertTrue(paymentRows.getFirst().isRequiresReview());
        assertEquals(0, service.getActiveSubscriptionInfo(users.findById(1).orElseThrow()).getUsedSessions());
    }

    @Test void expiredMixedHoldReleasesQuotaAndLatePaymentRequiresReview() {
        mixedPackage(); mixed("Card");
        current.set(current.get().plusSeconds(16 * 60));
        assertEquals(3, service.getActiveSubscriptionInfo(users.findById(1).orElseThrow()).getRemainingSessions());
        var listed = service.getMyClasses("1", "all", null).getClasses().getFirst();
        assertFalse(listed.isCanResumePayment()); assertTrue(listed.isCanRebook());
        assertEquals(0, listed.getHeldPackageSessions());
        paymentService.processIpn(callback(paymentRows.getFirst(), "00"));
        assertEquals("Paid", paymentRows.getFirst().getStatus()); assertTrue(paymentRows.getFirst().isRequiresReview());
        assertTrue(bookingRows.stream().allMatch(b -> "Cancelled".equals(b.getStatus())));
        assertEquals(0, service.getActiveSubscriptionInfo(users.findById(1).orElseThrow()).getUsedSessions());
    }

    @Test void mixedQrCreatesOnlySupplementPaymentAndRejectsStaleQuoteOrCash() {
        mixedPackage();
        var stale = MemberClassBookingRequest.builder().paymentMethod("MIXED").paymentChannel("Card")
                .packageSessions(2).paymentAmount(new BigDecimal("200000")).build();
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, stale, "127.0.0.1")).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> mixed("Cash"));
        assertTrue(bookingRows.isEmpty()); assertTrue(paymentRows.isEmpty());
        var result = mixed("E_Wallet");
        assertNull(result.getPaymentUrl()); assertEquals("Pending", result.getPaymentStatus());
        assertEquals(new BigDecimal("100000.00"), result.getPaymentAmount());
    }

    @Test
    void oneRegistrationPaysForAndListsFourRealSessionsThroughCourseEnd() {
        var preview = service.getClassConfirmationDetail("1", 10);
        assertEquals(4, preview.getSessionCount()); assertEquals(new BigDecimal("400000.00"), preview.getTotalAmount());
        var result = register("DIRECT", "Cash");
        assertEquals(4, bookingRows.size()); assertEquals(1, paymentRows.size());
        assertEquals(new BigDecimal("400000.00"), result.getPaymentAmount());
        var mine = service.getMyClasses("1", "all", "yoga");
        assertEquals(1, mine.getClasses().size()); assertEquals(1, mine.getCounts().getAll());
        var item = mine.getClasses().getFirst();
        assertEquals(List.of("2026-10-13", "2026-10-15", "2026-10-20", "2026-10-22"),
                item.getSessions().stream().map(s -> s.sessionDate().toString()).toList());
        current.set(Instant.parse("2026-10-26T00:00:00Z"));
        assertEquals("Completed", service.getMyClasses("1", "completed", null).getClasses().getFirst().getRawStatus());
        assertThrows(ResponseStatusException.class, () -> register("DIRECT", "Cash"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-13T00:30:00Z", "2026-10-13T00:30:01Z", "2026-10-16T00:00:00Z"})
    void registrationAndPreviewRejectStartedCourseEvenWithFutureSessions(String time) {
        current.set(Instant.parse(time));
        when(classes.searchClasses(isNull(), isNull(), isNull())).thenReturn(List.of(course));
        assertFalse(service.getAvailableClasses("1", null, null).getClasses().getFirst().isCanRegister());
        assertThrows(ResponseStatusException.class, () -> service.getClassConfirmationDetail("1", 10));
        for (String method : List.of("DIRECT", "PACKAGE")) {
            var error = assertThrows(ResponseStatusException.class, () -> register(method, "Cash"));
            assertEquals(400, error.getStatusCode().value());
            assertTrue(error.getReason().contains("Lớp đã bắt đầu"));
        }
        assertTrue(bookingRows.isEmpty()); assertTrue(paymentRows.isEmpty());
    }

    @Test
    void registrationIsAllowedImmediatelyBeforeFirstSession() {
        current.set(Instant.parse("2026-10-13T00:29:59Z"));
        assertEquals(4, register("DIRECT", "Cash").getSessionCount());
        assertTrue(service.getMyClasses("1", "all", null).getClasses().getFirst().isCanCancel());
    }

    @Test
    void gatewayConfirmsEverySessionOnceAndFailureOrExpiryReleasesWholeGroup() {
        register("DIRECT", "Card");
        assertTrue(bookingRows.stream().allMatch(b -> "Pending".equals(b.getStatus())));
        assertEquals("Pending", service.getMyClasses("1", "all", null).getClasses().getFirst().getRawStatus());
        Payment first = paymentRows.getFirst();
        assertEquals("00", paymentService.processIpn(callback(first, "00")).get("RspCode"));
        assertTrue(bookingRows.stream().allMatch(b -> "Booked".equals(b.getStatus())));
        assertEquals("02", paymentService.processIpn(callback(first, "00")).get("RspCode"));
        service.cancelClassBooking("1", bookingRows.getFirst().getId());
        assertEquals("Paid", first.getStatus()); assertTrue(first.isRequiresReview());
        register("DIRECT", "Card");
        Payment second = paymentRows.getLast();
        paymentService.processIpn(callback(second, "24"));
        assertTrue(bookingRows.stream().allMatch(b -> "Cancelled".equals(b.getStatus())));
        register("DIRECT", "Card");
        Payment third = paymentRows.getLast();
        current.set(current.get().plusSeconds(901));
        paymentService.getPayment("1", third.getId());
        assertTrue(bookingRows.stream().allMatch(b -> "Cancelled".equals(b.getStatus())));
        paymentService.processIpn(callback(third, "00"));
        assertTrue(third.isRequiresReview());
        assertTrue(bookingRows.stream().allMatch(b -> "Cancelled".equals(b.getStatus())));
        assertEquals(3, service.getMyClasses("1", "cancelled", null).getClasses().size());
    }

    @Test
    void availableCapacityUsesFutureSessionsAndDoesNotInventStudentsWhenCourseHasNoSessionsLeft() {
        when(classes.searchClasses(isNull(), isNull(), isNull())).thenReturn(List.of(course));
        when(bookings.countReservedMembersBySessionId(eq(sessionRows.getLast().getId()), any())).thenReturn(10L);
        assertFalse(service.getAvailableClasses("1", null, null).getClasses().getFirst().isCanRegister());
        current.set(Instant.parse("2026-10-23T00:00:00Z"));
        var item = service.getAvailableClasses("1", null, null).getClasses().getFirst();
        assertEquals(0, item.getEnrolledCount()); assertEquals(0, item.getRemainingSlots());
        assertNull(item.getSessionId()); assertFalse(item.isCanRegister());
    }

    @Test
    void oneCancelledSessionPreventsPartialConfirmationOfAPaidGroup() {
        register("DIRECT", "Card");
        sessionRows.getLast().setStatus("Cancelled");
        Payment payment = paymentRows.getFirst();
        paymentService.processIpn(callback(payment, "00"));
        assertEquals("Paid", payment.getStatus()); assertTrue(payment.isRequiresReview());
        assertTrue(bookingRows.stream().allMatch(b -> "Cancelled".equals(b.getStatus())));
        assertTrue(service.getMyClasses("1", "ongoing", null).getClasses().isEmpty());
    }

    @Test
    void insufficientPackageOrOneFullSessionWritesNoPartialRegistration() {
        when(includedSports.hasActiveIncludedEntitlement(eq(1), eq(1), any())).thenReturn(true);
        MembershipPackage pkg = new MembershipPackage(); pkg.setIncludedClasses(3);
        MemberSubscription sub = MemberSubscription.builder().membershipPackage(pkg)
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2026, 10, 31)).build();
        when(subscriptions.findCurrentActiveSubscription(eq(1), any())).thenReturn(Optional.of(sub));
        assertThrows(ResponseStatusException.class, () -> register("PACKAGE", null));
        assertTrue(bookingRows.isEmpty());
        pkg.setIncludedClasses(10); sub.setEndDate(LocalDate.of(2026, 10, 18));
        assertThrows(ResponseStatusException.class, () -> register("PACKAGE", null));
        sub.setEndDate(LocalDate.of(2026, 10, 31));
        when(bookings.countReservedMembersBySessionId(eq(sessionRows.getLast().getId()), any())).thenReturn(10L);
        assertThrows(ResponseStatusException.class, () -> register("DIRECT", "Cash"));
        assertTrue(bookingRows.isEmpty()); assertTrue(paymentRows.isEmpty());
        when(bookings.countReservedMembersBySessionId(eq(sessionRows.getLast().getId()), any())).thenReturn(1L);
        assertEquals(6, register("PACKAGE", null).getRemainingPackageSessions());
        assertEquals(4, bookingRows.size()); assertTrue(paymentRows.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-13T00:30:00Z", "2026-10-13T00:30:01Z", "2026-10-16T00:00:00Z"})
    void cancellationRejectsStartedCourseIncludingConfirmationOpenedBeforeStart(String time) {
        register("DIRECT", "Cash");
        int bookingId = bookingRows.getFirst().getId();
        var preview = service.cancellationPreview("1", bookingId);
        assertEquals(4, preview.cancelSessions());
        current.set(Instant.parse(time));
        assertFalse(service.getMyClasses("1", "all", null).getClasses().getFirst().isCanCancel());
        assertThrows(ResponseStatusException.class, () -> service.cancellationPreview("1", bookingId));
        var error = assertThrows(ResponseStatusException.class, () -> service.cancelClassBooking("1", bookingId,
                new RefundDtos.CancelConfirmation(preview.estimatedRefund(), preview.cancelSessions())));
        assertEquals(400, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Lớp đã bắt đầu"));
        assertTrue(bookingRows.stream().allMatch(b -> "Booked".equals(b.getStatus())));
        assertEquals("Paid", paymentRows.getFirst().getStatus());
        assertFalse(paymentRows.getFirst().isRequiresReview());
    }

    @Test
    void legacyBookingDoesNotExpandIntoWholeCourse() {
        bookingRows.clear();
        bookingRows.add(ClassBooking.builder().id(99).user(users.findById(1).orElseThrow()).session(sessionRows.getFirst())
                .status("Booked").paymentMethod("DIRECT").build());
        assertEquals(1, service.getMyClasses("1", "all", null).getClasses().getFirst().getSessions().size());
    }
}
