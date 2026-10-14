package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberClassBookingRequest;
import com.swp391.beswp.dto.MemberClassBookingResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: requires a separately prepared, migrated SQL Server database whose name ends in _test. No DDL. */
@EnabledIfEnvironmentVariable(named = "VNPAY_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${VNPAY_TEST_DB_URL}",
        "spring.datasource.username=${VNPAY_TEST_DB_USERNAME}",
        "spring.datasource.password=${VNPAY_TEST_DB_PASSWORD}",
        "app.vnpay.tmn-code=TESTCODE", "app.vnpay.hash-secret=integration-test-secret",
        "app.vnpay.return-url=http://localhost:8080/api/payments/vnpay/return",
        "app.vnpay.frontend-url=http://localhost:5173"
})
class VnpaySqlServerIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberClassBookingService bookings;
    @Autowired MemberPaymentService payments;
    @Autowired VnpayService vnpay;
    private Integer classId, subjectId, roomId;
    private final List<Integer> userIds = new ArrayList<>();
    private final MemberClassBookingRequest card = MemberClassBookingRequest.builder().paymentMethod("DIRECT").paymentChannel("Card").build();

    @BeforeEach
    void fixtures() {
        assertTrue(jdbc.queryForObject("SELECT DB_NAME()", String.class).endsWith("_test"), "Use a dedicated _test database");
        String name = "vnpay_" + UUID.randomUUID().toString().replace("-", "");
        int memberRole = jdbc.queryForObject("SELECT Role_ID FROM dbo.Role WHERE Role_Name = 'Member'", Integer.class);
        int coachRole = jdbc.queryForObject("SELECT Role_ID FROM dbo.Role WHERE Role_Name = 'Coach'", Integer.class);
        for (int i = 0; i < 3; i++) {
            String username = name + i;
            userIds.add(jdbc.queryForObject("INSERT INTO dbo.[User] (Role_ID, FullName, Username, Email, Password) "
                    + "OUTPUT INSERTED.User_ID VALUES (?, ?, ?, ?, ?)", Integer.class,
                    i == 2 ? coachRole : memberRole, username, username, username + "@test.example", "test-only-unused-hash"));
        }
        subjectId = jdbc.queryForObject("INSERT INTO dbo.Subject (Name) OUTPUT INSERTED.Subject_ID VALUES (?)", Integer.class, name);
        roomId = jdbc.queryForObject("INSERT INTO dbo.Room (Name, Max_Capacity) OUTPUT INSERTED.Room_ID VALUES (?, 1)", Integer.class, name);
        LocalDate firstDate = LocalDate.now(VnpayService.OFFSET).plusDays(1);
        classId = jdbc.queryForObject("INSERT INTO dbo.Class (Subject_ID, Coach_ID, Room_ID, Name, Max_Capacity, Price, Date, Start_Time, End_Time, Duration_Weeks, End_Date, Days_Of_Week) "
                + "OUTPUT INSERTED.Class_ID VALUES (?, ?, ?, ?, 1, 100000, ?, '08:00:00', '09:00:00', 1, ?, ?)", Integer.class,
                subjectId, userIds.get(2), roomId, name, firstDate, ClassSchedule.endDate(firstDate, 1), ClassSchedule.code(firstDate.getDayOfWeek()));
        jdbc.update("INSERT INTO dbo.Class_Session (Class_ID, Date, Start_Time, End_Time, Status) VALUES (?, ?, '08:00:00', '09:00:00', 'Scheduled')", classId, firstDate);
    }

    @AfterEach
    void cleanupOnlyOwnFixtures() {
        if (classId != null) {
            jdbc.update("DELETE p FROM dbo.Payment p JOIN dbo.Class_Booking b ON b.Booking_ID=p.Booking_ID JOIN dbo.Class_Session s ON s.Session_ID=b.Session_ID WHERE s.Class_ID=?", classId);
            jdbc.update("DELETE b FROM dbo.Class_Booking b JOIN dbo.Class_Session s ON s.Session_ID=b.Session_ID WHERE s.Class_ID=?", classId);
            jdbc.update("DELETE FROM dbo.Class_Session WHERE Class_ID=?", classId);
            jdbc.update("DELETE FROM dbo.Class WHERE Class_ID=?", classId);
        }
        for (int id : userIds) {
            jdbc.update("DELETE FROM dbo.Activity_Log WHERE User_ID=?", id);
            jdbc.update("DELETE FROM dbo.[User] WHERE User_ID=?", id);
        }
        if (subjectId != null) jdbc.update("DELETE FROM dbo.Subject WHERE Subject_ID=?", subjectId);
        if (roomId != null) jdbc.update("DELETE FROM dbo.Room WHERE Room_ID=?", roomId);
    }

    @Test
    void realTransactionsProtectLastSeatAndConcurrentIpnIsIdempotent() throws Exception {
        var registrations = concurrent(() -> register(0), () -> register(1));
        assertEquals(1, registrations.stream().filter(Objects::nonNull).count());
        MemberClassBookingResponse registration = registrations.stream().filter(Objects::nonNull).findFirst().orElseThrow();
        Map<String, String> callback = callback(registration.getPaymentId());
        var results = concurrent(() -> payments.processIpn(callback).get("RspCode"), () -> payments.processIpn(callback).get("RspCode"));
        assertEquals(Set.of("00", "02"), new HashSet<>(results));
        assertEquals("Paid", jdbc.queryForObject("SELECT Status FROM dbo.Payment WHERE Payment_ID=?", String.class, registration.getPaymentId()));
        assertEquals("Booked", jdbc.queryForObject("SELECT Status FROM dbo.Class_Booking WHERE Booking_ID=?", String.class, registration.getBookingId()));
    }

    @Test
    void realExpiryAndRetryRespectUniqueConstraintAndKeepLatePayment() {
        var first = register(0); assertNotNull(first);
        assertThrows(ResponseStatusException.class, () -> payments.getPayment(userIds.get(1).toString(), first.getPaymentId()));
        LocalDateTime expired = LocalDateTime.now(VnpayService.OFFSET).minusMinutes(1).withNano(0);
        jdbc.update("UPDATE dbo.Payment SET Expires_At=? WHERE Payment_ID=?", expired, first.getPaymentId());
        jdbc.update("UPDATE dbo.Class_Booking SET Payment_Expires_At=? WHERE Booking_ID=?", expired, first.getBookingId());
        assertEquals("Failed", payments.getPayment(userIds.getFirst().toString(), first.getPaymentId()).getPaymentStatus());
        var retry = register(0); assertNotNull(retry);
        assertEquals(first.getBookingId(), retry.getBookingId()); assertNotEquals(first.getPaymentId(), retry.getPaymentId());
        assertEquals("00", payments.processIpn(callback(first.getPaymentId())).get("RspCode"));
        var old = payments.getPayment(userIds.getFirst().toString(), first.getPaymentId());
        assertEquals("Paid", old.getPaymentStatus()); assertTrue(old.isRequiresReview());
        assertEquals("Pending", payments.getPayment(userIds.getFirst().toString(), retry.getPaymentId()).getPaymentStatus());
    }

    private MemberClassBookingResponse register(int memberIndex) {
        try { return bookings.registerClass(userIds.get(memberIndex).toString(), classId, card, "127.0.0.1"); }
        catch (ResponseStatusException e) { assertEquals(400, e.getStatusCode().value()); return null; }
    }
    private Map<String, String> callback(int paymentId) {
        Map<String, String> values = new HashMap<>(Map.of("vnp_TmnCode", "TESTCODE", "vnp_TxnRef", Integer.toString(paymentId),
                "vnp_Amount", "10000000", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00", "vnp_TransactionNo", "123456"));
        values.put("vnp_SecureHash", vnpay.sign(values)); return values;
    }
    private <T> List<T> concurrent(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<T> a = executor.submit(() -> { start.await(); return first.call(); });
            Future<T> b = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return Arrays.asList(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }
}
