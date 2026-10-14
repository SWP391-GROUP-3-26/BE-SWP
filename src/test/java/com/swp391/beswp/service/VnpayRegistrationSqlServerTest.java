package com.swp391.beswp.service;

import com.microsoft.sqlserver.jdbc.SQLServerConnection;
import com.swp391.beswp.config.VnpayConfig;
import com.swp391.beswp.dto.CreateClassRequest;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.ClassSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Opt-in local SQL Server check. All fixture/request writes roll back; no DDL or cleanup deletes. */
@EnabledIfEnvironmentVariable(named = "VNPAY_REGISTRATION_DB_CHECK", matches = "true")
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class VnpayRegistrationSqlServerTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClassRepository classes;
    @Autowired ClassService classService;
    @Autowired ClassSessionRepository sessions;
    @Autowired VnpayConfig config;
    @Autowired VnpayService vnpay;
    @Autowired ConfigurableEnvironment environment;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entityManager;

    @Test
    void timeParameterMatchesExistingSqlServerSession() throws Exception {
        try (var connection = dataSource.getConnection()) {
            var driver = connection.unwrap(SQLServerConnection.class);
            assertFalse(driver.getSendTimeAsDatetime());
            // A SELECT against a typed TIME value reproduces error 402 when this flag is true.
            driver.setSendTimeAsDatetime(true);
            try (var query = connection.prepareStatement("SELECT CASE WHEN CAST('07:30:00' AS TIME) = ? THEN 1 ELSE 0 END")) {
                query.setTime(1, Time.valueOf("07:30:00"));
                var error = assertThrows(java.sql.SQLException.class, query::executeQuery);
                assertEquals(402, error.getErrorCode());
            } finally {
                driver.setSendTimeAsDatetime(false);
            }
            try (var query = connection.prepareStatement("SELECT CASE WHEN CAST('07:30:00' AS TIME) = ? THEN 1 ELSE 0 END")) {
                query.setTime(1, Time.valueOf("07:30:00"));
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
            }
        }
        var slot = jdbc.queryForMap("SELECT TOP (1) Session_ID, Class_ID, Date, Start_Time FROM dbo.Class_Session ORDER BY Session_ID");
        var session = sessions.findByClassEntity_IdAndDateAndStartTime(
                ((Number) slot.get("Class_ID")).intValue(),
                ((java.sql.Date) slot.get("Date")).toLocalDate(),
                ((Time) slot.get("Start_Time")).toLocalTime());
        assertEquals(((Number) slot.get("Session_ID")).intValue(), session.orElseThrow().getId());
    }

    @Test
    void realLoginAndCardRequestCreatePendingPaymentWithLoadedConfiguration() throws Exception {
        assertEquals(environment.getProperty("VNPAY_TMN_CODE"), config.getTmnCode(), "TmnCode binding differs");
        assertTrue(java.util.Objects.equals(environment.getProperty("VNPAY_HASH_SECRET"), config.getHashSecret()),
                "HashSecret binding differs");
        assertEquals(environment.getProperty("VNPAY_RETURN_URL"), config.getReturnUrl(), "Return URL binding differs");
        assertEquals(environment.getProperty("VNPAY_FRONTEND_URL"), config.getFrontendUrl(), "Frontend URL binding differs");
        vnpay.requireConfigured();
        for (String key : new String[]{"VNPAY_TMN_CODE", "VNPAY_HASH_SECRET", "VNPAY_RETURN_URL", "VNPAY_FRONTEND_URL"}) {
            String source = environment.getPropertySources().stream()
                    .filter(p -> !p.getName().equals("configurationProperties") && p.containsProperty(key))
                    .findFirst().orElseThrow().getName();
            System.out.println(key + " source: " + source);
        }
        System.out.println("VNPay runtime configuration: all four bindings match; configuration validation passed");
        System.out.println("Runtime database: " + jdbc.queryForObject("SELECT DB_NAME()", String.class));
        System.out.println("Working directory: " + System.getProperty("user.dir"));

        var login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier", System.getenv("VNPAY_CHECK_MEMBER"),
                        "password", System.getenv("VNPAY_CHECK_PASSWORD"))))).andReturn();
        assertEquals(200, login.getResponse().getStatus(), "Sample Member login failed");
        String token = json.readTree(login.getResponse().getContentAsString()).at("/data/accessToken").asText();
        assertFalse(token.isBlank(), "Login did not return an access token");

        ClassEntity template = classes.findAll().stream()
                .filter(c -> "Active".equalsIgnoreCase(c.getStatus()) || "Open".equalsIgnoreCase(c.getStatus()))
                .findFirst().orElseThrow();
        LocalDate firstDate = LocalDate.now(VnpayService.OFFSET).plusDays(1);
        var createdClass = classService.createClass(CreateClassRequest.builder()
                .subjectId(template.getSubject().getId()).coachId(template.getCoach().getId()).roomId(template.getRoom().getId())
                .name("VNPay diagnostic " + UUID.randomUUID()).status("Active").maxCapacity(1)
                .price(new java.math.BigDecimal("100000.00"))
                .startDate(firstDate).durationWeeks(1).daysOfWeek(java.util.List.of(ClassSchedule.code(firstDate.getDayOfWeek())))
                .slotId(3).startTime(LocalTime.of(12, 30)).endTime(LocalTime.of(13, 30)).build());
        ClassEntity testClass = classes.findById(createdClass.getData().getId()).orElseThrow();
        var result = mvc.perform(post("/api/member/classes/{id}/register", testClass.getId())
                .header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"paymentMethod\":\"DIRECT\",\"paymentChannel\":\"Card\"}")).andReturn();
        assertEquals(201, result.getResponse().getStatus(), "Card registration failed; check backend exception log");
        var response = json.readTree(result.getResponse().getContentAsString());
        assertTrue(response.get("success").asBoolean());
        assertEquals("Pending", response.get("paymentStatus").asText());
        assertEquals("Chờ thanh toán", response.get("bookingStatus").asText());
        assertTrue(response.get("paymentUrl").asText().startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(response.get("paymentUrl").asText().contains("vnp_SecureHash="));
        entityManager.flush();
        int paymentId = response.get("paymentId").asInt();
        int bookingId = response.get("bookingId").asInt();
        assertEquals("Pending", jdbc.queryForObject("SELECT Status FROM dbo.Payment WHERE Payment_ID=?", String.class, paymentId));
        assertEquals("Pending", jdbc.queryForObject("SELECT Status FROM dbo.Class_Booking WHERE Booking_ID=?", String.class, bookingId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Activity_Log WHERE Action_Type='BOOK_CLASS' AND Description LIKE ?",
                Integer.class, "%" + testClass.getName() + "%"));
        System.out.println("Authenticated DIRECT + Card: HTTP 201; session, Pending booking/payment, signed URL and activity log verified; transaction will roll back");
    }
}
