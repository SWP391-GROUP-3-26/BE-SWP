package com.swp391.beswp.service;

import com.swp391.beswp.config.VnpayConfig;
import com.swp391.beswp.dto.MemberClassBookingRequest;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MemberClassBookingServiceTest {
    private final ClassRepository classes = mock(ClassRepository.class);
    private final ClassSessionRepository sessions = mock(ClassSessionRepository.class);
    private final ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    private final MemberSubscriptionRepository subscriptions = mock(MemberSubscriptionRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ActivityLogRepository logs = mock(ActivityLogRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2026-10-13T01:00:00Z"));
    private final List<ClassBooking> bookingRows = new CopyOnWriteArrayList<>();
    private final List<Payment> paymentRows = new CopyOnWriteArrayList<>();
    private final AtomicInteger paymentSequence = new AtomicInteger(105);
    private MemberClassBookingService service;
    private ClassEntity classEntity;
    private ClassSession session;
    private User member;
    private final MemberClassBookingRequest card = MemberClassBookingRequest.builder().paymentMethod("DIRECT").paymentChannel("Card").build();

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(VnpayService.OFFSET);
        when(clock.instant()).thenAnswer(i -> instant.get());
        member = member(1);
        when(users.findById(anyInt())).thenAnswer(i -> Optional.of(member(i.getArgument(0))));
        when(users.findByIdForUpdate(anyInt())).thenAnswer(i -> Optional.of(member(i.getArgument(0))));
        classEntity = ClassEntity.builder().id(10).status("Active").name("Yoga").maxCapacity(1).price(new BigDecimal("100000.00")).build();
        session = ClassSession.builder().id(20).classEntity(classEntity).status("Scheduled")
                .date(LocalDate.of(2026, 10, 14)).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).build();
        when(classes.findByIdForUpdate(10)).thenReturn(Optional.of(classEntity));
        when(sessions.findUpcomingScheduledSessions(eq(10), any())).thenReturn(List.of(session));
        when(bookings.findByUser_IdAndSession_Id(anyInt(), eq(20))).thenAnswer(i -> bookingRows.stream()
                .filter(b -> b.getUser().getId().equals(i.getArgument(0))).findFirst());
        when(bookings.existsActiveBookingByUserIdAndClassId(anyInt(), eq(10), any())).thenAnswer(i -> bookingRows.stream()
                .anyMatch(b -> b.getUser().getId().equals(i.getArgument(0)) && reserved(b, i.getArgument(2))));
        when(bookings.countReservedMembersByClassId(eq(10), any())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> reserved(b, i.getArgument(1))).count());
        when(bookings.countReservedMembersBySessionId(anyInt(), any())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> b.getSession().getId().equals(i.getArgument(0)) && reserved(b, i.getArgument(1)))
                .map(b -> b.getUser().getId()).distinct().count());
        when(bookings.findExpiredByClassId(eq(10), any())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> "Pending".equals(b.getStatus()) && !b.getPaymentExpiresAt().isAfter(i.getArgument(1))).toList());
        when(bookings.save(any())).thenAnswer(i -> {
            ClassBooking b = i.getArgument(0);
            if (b.getId() == null) { b.setId(42 + bookingRows.size()); bookingRows.add(b); }
            return b;
        });
        when(bookings.findByRegistrationCodeOrderByIdAsc(anyString())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> i.getArgument(0).equals(b.getRegistrationCode())).sorted(Comparator.comparing(ClassBooking::getId)).toList());
        when(bookings.findMyBookings(anyInt(), isNull(), isNull())).thenAnswer(i -> bookingRows.stream()
                .filter(b -> b.getUser().getId().equals(i.getArgument(0))).toList());
        when(payments.save(any())).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(paymentSequence.getAndIncrement()); paymentRows.add(p); return p;
        });
        when(payments.findAllByBooking_IdOrderByIdDesc(anyInt())).thenAnswer(i -> paymentRows.stream()
                .filter(p -> p.getBooking().getId().equals(i.getArgument(0)))
                .sorted(Comparator.comparing(Payment::getId).reversed()).toList());
        service = newService(VnpayServiceTest.configured());
    }

    private MemberClassBookingService newService(VnpayService vnpay) {
        return new MemberClassBookingService(classes, sessions, bookings, subscriptions, payments, logs, users, subjects,
                vnpay, mock(SepayService.class), new ClassReservationService(bookings, payments), clock);
    }
    private User member(int id) {
        Role role = new Role(); role.setRoleName("Member");
        User user = new User(); user.setId(id); user.setRole(role); user.setFullName("Member " + id); return user;
    }
    private boolean reserved(ClassBooking b, LocalDateTime now) {
        return "Booked".equals(b.getStatus()) || ("Pending".equals(b.getStatus()) && b.getPaymentExpiresAt().isAfter(now));
    }

    @Test
    void cardCreatesPendingFromDatabasePriceAndLocksBeforeCheckingCapacity() {
        var response = service.registerClass("1", 10, card, "127.0.0.1");
        assertTrue(response.isSuccess()); assertEquals("Chờ thanh toán", response.getBookingStatus());
        assertEquals("Pending", response.getPaymentStatus()); assertEquals(105, response.getPaymentId());
        assertEquals(new BigDecimal("100000.00"), response.getPaymentAmount());
        assertTrue(response.getPaymentUrl().contains("vnp_BankCode=VNBANK"));
        assertEquals(VnpayService.OFFSET, response.getExpiresAt().getOffset());
        assertEquals(Duration.ofMinutes(15), Duration.between(response.getBookingDatetime(), response.getExpiresAt().toLocalDateTime()));
        assertEquals("Pending", bookingRows.getFirst().getStatus());
        assertEquals("Card", paymentRows.getFirst().getMethod());
        var order = inOrder(users, classes, bookings);
        order.verify(users).findByIdForUpdate(1);
        order.verify(classes).findByIdForUpdate(10);
        order.verify(bookings).findExpiredByClassId(eq(10), any());
        order.verify(bookings).existsActiveBookingByUserIdAndClassId(eq(1), eq(10), any());
        order.verify(bookings).countReservedMembersBySessionId(eq(20), any());
        order.verify(bookings).save(any());
    }

    @Test
    void walletCreatesPendingPaymentAndReservesSeatUntilWebhook() {
        var response = service.registerClass("1", 10, MemberClassBookingRequest.builder()
                .paymentMethod("DIRECT").paymentChannel("E_Wallet").build(), "127.0.0.1");
        assertEquals("Pending", response.getPaymentStatus()); assertEquals("Chờ thanh toán", response.getBookingStatus());
        assertNull(response.getPaymentUrl()); assertNotNull(response.getExpiresAt());
        assertEquals("Pending", bookingRows.getFirst().getStatus());
        assertEquals("E_Wallet", paymentRows.getFirst().getMethod());
        assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, card, "127.0.0.1"));
    }

    @Test
    void unconfiguredWalletDoesNotCreateBookingOrPayment() {
        var sepay = new SepayService(new com.swp391.beswp.config.SepayConfig(), payments, classes, bookings,
                new ClassReservationService(bookings, payments), clock, mock(OnlineSubscriptionPaymentService.class));
        var unconfigured = new MemberClassBookingService(classes, sessions, bookings, subscriptions, payments, logs, users,
                subjects, VnpayServiceTest.configured(), sepay, new ClassReservationService(bookings, payments), clock);
        var error = assertThrows(ResponseStatusException.class, () -> unconfigured.registerClass("1", 10,
                MemberClassBookingRequest.builder().paymentMethod("DIRECT").paymentChannel("E_Wallet").build(), "127.0.0.1"));
        assertEquals(503, error.getStatusCode().value()); assertTrue(bookingRows.isEmpty()); assertTrue(paymentRows.isEmpty());
    }

    @Test
    void cardCannotCreateShortenedPaymentWindowNearSessionStart() {
        session.setDate(LocalDate.now(clock));
        session.setStartTime(LocalTime.now(clock).plusMinutes(10));
        var error = assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, card, "127.0.0.1"));
        assertEquals(400, error.getStatusCode().value());
        assertTrue(error.getReason().contains("15 phút"));
        assertTrue(bookingRows.isEmpty()); assertTrue(paymentRows.isEmpty());
    }

    @Test
    void pendingPreventsDuplicatesAndAlsoBlocksPackageSeat() {
        service.registerClass("1", 10, card, "127.0.0.1");
        var duplicate = assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, card, "127.0.0.1"));
        assertEquals(409, duplicate.getStatusCode().value());
        var full = assertThrows(ResponseStatusException.class, () -> service.registerClass("2", 10,
                MemberClassBookingRequest.builder().paymentMethod("PACKAGE").build(), "127.0.0.1"));
        assertEquals(400, full.getStatusCode().value()); assertEquals(1, paymentRows.size());
        verifyNoInteractions(subscriptions);
    }

    @Test
    void expiryReleasesSeatAndRetryKeepsOldRegistrationHistory() {
        var first = service.registerClass("1", 10, card, "127.0.0.1");
        instant.set(instant.get().plusSeconds(15 * 60));
        var retry = service.registerClass("1", 10, card, "127.0.0.1");
        assertNotEquals(first.getBookingId(), retry.getBookingId()); assertNotEquals(first.getPaymentId(), retry.getPaymentId());
        assertEquals(2, bookingRows.size()); assertEquals(2, paymentRows.size());
        assertEquals("Cancelled", bookingRows.getFirst().getStatus());
        assertEquals("Failed", paymentRows.getFirst().getStatus()); assertEquals("Pending", paymentRows.getLast().getStatus());
    }

    @Test
    void expiredReservationAllowsDifferentMemberToTakeLastSeat() {
        service.registerClass("1", 10, card, "127.0.0.1");
        instant.set(instant.get().plusSeconds(15 * 60));
        service.registerClass("2", 10, card, "127.0.0.1");
        assertEquals("Cancelled", bookingRows.getFirst().getStatus());
        assertEquals("Pending", bookingRows.getLast().getStatus());
    }

    @Test
    void packageConfirmsWithoutGatewayAndCountsOnlyPackageBookings() {
        MembershipPackage pkg = new MembershipPackage(); pkg.setIncludedClasses(5); pkg.setName("Test package");
        MemberSubscription sub = MemberSubscription.builder().membershipPackage(pkg).startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31)).build();
        when(subscriptions.findCurrentActiveSubscription(eq(1), any())).thenReturn(Optional.of(sub));
        when(bookings.countUsedPackageSessions(eq(1), any(), any())).thenReturn(2L);
        var response = service.registerClass("1", 10, MemberClassBookingRequest.builder().paymentMethod("PACKAGE").build(), "127.0.0.1");
        assertEquals("Booked", bookingRows.getFirst().getStatus()); assertEquals("PACKAGE", bookingRows.getFirst().getPaymentMethod());
        assertEquals(2, response.getRemainingPackageSessions()); assertNull(response.getPaymentUrl());
        assertNull(response.getPaymentId()); assertTrue(paymentRows.isEmpty());
    }

    @Test
    void missingConfigInvalidPriceOrClosedClassWritesNothing() {
        service = newService(new VnpayService(new VnpayConfig()));
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, card, "127.0.0.1")).getStatusCode().value());
        service = newService(VnpayServiceTest.configured());
        classEntity.setPrice(new BigDecimal("100.50"));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, card, "127.0.0.1")).getStatusCode().value());
        classEntity.setStatus("Closed");
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, card, "127.0.0.1")).getStatusCode().value());
        assertTrue(bookingRows.isEmpty()); assertTrue(paymentRows.isEmpty());
    }

    @Test
    void cancelVnpayPaidBookingDoesNotPretendRefundWasCompleted() {
        service.registerClass("1", 10, card, "127.0.0.1");
        ClassBooking booking = bookingRows.getFirst(); Payment payment = paymentRows.getFirst();
        booking.setStatus("Booked"); payment.setStatus("Paid");
        when(bookings.findOwnedClassId(42, 1)).thenReturn(Optional.of(10));
        when(bookings.findByIdAndUser_Id(42, 1)).thenReturn(Optional.of(booking));
        service.cancelClassBooking("1", 42);
        assertEquals("Cancelled", booking.getStatus()); assertEquals("Paid", payment.getStatus()); assertTrue(payment.isRequiresReview());
    }

    @Test
    void concurrentLastSeatRequestsSerializeUntilTransactionCompletion() throws Exception {
        // In-memory repository lock exercises service ordering; SQL Server lock behavior needs the opt-in DB check.
        ReentrantLock classLock = new ReentrantLock();
        when(classes.findByIdForUpdate(10)).thenAnswer(i -> { classLock.lock(); return Optional.of(classEntity); });
        var manager = new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition def) { }
            protected void doCommit(DefaultTransactionStatus status) { if (classLock.isHeldByCurrentThread()) classLock.unlock(); }
            protected void doRollback(DefaultTransactionStatus status) { if (classLock.isHeldByCurrentThread()) classLock.unlock(); }
        };
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (String id : List.of("1", "2")) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return new TransactionTemplate(manager).execute(status -> {
                            service.registerClass(id, 10, card, "127.0.0.1"); return true;
                        });
                    } catch (ResponseStatusException e) { return false; }
                }));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> future : futures) if (future.get(10, TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted); assertEquals(1, bookingRows.size()); assertEquals(1, paymentRows.size());
        }
    }

    private void boundedCourse() {
        classEntity.setDate(LocalDate.of(2026, 10, 13)); classEntity.setDurationWeeks(1);
        classEntity.setEndDate(LocalDate.of(2026, 10, 18)); classEntity.setDaysOfWeek("Thứ 3, Thứ 5");
        session.setDate(LocalDate.of(2026, 10, 15));
        when(classes.findById(10)).thenReturn(Optional.of(classEntity));
    }

    @Test
    void registrationNeverGeneratesSessionsOrBooksOutsideCourseDates() {
        boundedCourse();
        for (LocalDate date : List.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 20))) {
            session.setDate(date);
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.registerClass("1", 10, card, "127.0.0.1")).getStatusCode().value());
        }
        when(sessions.findUpcomingScheduledSessions(eq(10), any())).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, card, "127.0.0.1"));
        verify(sessions, never()).save(any()); verify(bookings, never()).save(any()); verify(payments, never()).save(any());
    }

    @Test
    void expiredActiveClassIsHiddenAndConfirmationAndRegistrationRejectItWithoutScheduler() {
        boundedCourse(); instant.set(Instant.parse("2026-10-19T01:00:00Z"));
        when(classes.searchClasses(null, null, null)).thenReturn(List.of(classEntity));
        assertTrue(service.getAvailableClasses("1", null, null).getClasses().isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.getClassConfirmationDetail("1", 10));
        assertThrows(ResponseStatusException.class, () -> service.registerClass("1", 10, card, "127.0.0.1"));
        verify(bookings, never()).save(any()); verify(payments, never()).save(any());
    }

    @Test
    void lastDayStillAllowsFutureSessionAndReturnsActualSessionAndTerm() {
        boundedCourse(); classEntity.setDaysOfWeek("T3, CN"); session.setDate(LocalDate.of(2026, 10, 18));
        session.setStartTime(LocalTime.of(9, 0)); session.setEndTime(LocalTime.of(10, 0));
        instant.set(Instant.parse("2026-10-18T01:00:00Z"));
        var response = service.registerClass("1", 10, card, "127.0.0.1");
        assertEquals("Pending", response.getPaymentStatus()); assertEquals(1, response.getDurationWeeks());
        assertEquals(LocalDate.of(2026, 10, 18), response.getEndDate());
        assertEquals(session.getDate(), response.getSessionDate()); assertEquals(session.getStartTime(), response.getSessionStartTime());
        verify(sessions, never()).save(any());
    }

    @Test
    void memberCalendarShowsActualSessionAndCompletedStatusBeforeSchedulerHasCaughtUp() {
        boundedCourse();
        ClassBooking booking = ClassBooking.builder().id(42).user(member).session(session).status("Booked")
                .bookingDatetime(LocalDateTime.of(2026, 10, 13, 8, 0)).paymentMethod("PACKAGE").build();
        when(bookings.findMyBookings(1, null, null)).thenReturn(List.of(booking));
        var active = service.getMyClasses("1", "ongoing", null).getClasses().getFirst();
        assertEquals(session.getDate(), active.getSessionDate()); assertEquals(session.getEndTime(), active.getSessionEndTime());
        assertEquals(1, active.getDurationWeeks());
        instant.set(Instant.parse("2026-10-19T01:00:00Z"));
        assertTrue(service.getMyClasses("1", "ongoing", null).getClasses().isEmpty());
        var result = service.getMyClasses("1", "completed", null);
        assertEquals(1, result.getCounts().getCompleted()); assertEquals(0, result.getCounts().getOngoing());
        var completed = result.getClasses().getFirst();
        assertEquals("Completed", completed.getRawStatus()); assertEquals("Completed", completed.getClassStatus());
        assertFalse(completed.isCanCancel()); assertFalse(completed.isCanRebook());
        assertEquals("Booked", booking.getStatus());
    }
}
