package com.swp391.beswp.service;

import com.swp391.beswp.dto.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                users, subjects, vnpay, mock(SepayService.class), reservations, clock);
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

    @Test
    void joiningMidCourseChargesOnlyRemainingSessionsAndCannotDuplicate() {
        current.set(Instant.parse("2026-10-16T00:00:00Z"));
        assertEquals(2, register("DIRECT", "Cash").getSessionCount());
        assertEquals(new BigDecimal("200000.00"), paymentRows.getFirst().getAmount());
        assertThrows(ResponseStatusException.class, () -> register("DIRECT", "Cash"));
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

    @Test
    void cancellationKeepsAttendedSessionsAndLegacyBookingDoesNotExpandIntoWholeCourse() {
        register("DIRECT", "Cash");
        current.set(Instant.parse("2026-10-16T00:00:00Z"));
        service.cancelClassBooking("1", bookingRows.getFirst().getId());
        assertEquals(List.of("Completed", "Completed", "Cancelled", "Cancelled"), bookingRows.stream().map(ClassBooking::getStatus).toList());
        assertEquals("Cancelled", service.getMyClasses("1", "all", null).getClasses().getFirst().getRawStatus());
        bookingRows.clear();
        bookingRows.add(ClassBooking.builder().id(99).user(users.findById(1).orElseThrow()).session(sessionRows.getFirst())
                .status("Booked").paymentMethod("DIRECT").build());
        assertEquals(1, service.getMyClasses("1", "all", null).getClasses().getFirst().getSessions().size());
    }
}
