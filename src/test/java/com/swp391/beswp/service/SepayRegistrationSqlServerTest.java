package com.swp391.beswp.service;

import com.swp391.beswp.dto.CreateClassRequest;
import com.swp391.beswp.repository.ClassRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in local DB check. Every fixture and payment mutation rolls back; no DDL. */
@EnabledIfEnvironmentVariable(named = "SEPAY_DB_CHECK", matches = "true")
@SpringBootTest(properties = {"spring.jpa.show-sql=false", "app.sepay.enabled=true", "app.sepay.test-mode=true",
        "app.sepay.bank-code=MBBank", "app.sepay.account-number=0123456789", "app.sepay.account-name=TEST ONLY",
        "app.sepay.webhook-api-key=integration-test-only-key-at-least-32-characters"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class SepayRegistrationSqlServerTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClassRepository classes;
    @Autowired ClassService classService;
    @Autowired EntityManager entityManager;

    @Test
    void memberQrWebhookAndDatabaseStateAgreeAndDuplicateIsHarmless() throws Exception {
        assertEquals("swp391_db", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        int memberId = jdbc.queryForObject("SELECT TOP (1) u.User_ID FROM dbo.[User] u JOIN dbo.Role r ON u.Role_ID=r.Role_ID WHERE r.Role_Name=N'Member' AND u.Status=N'Active'", Integer.class);
        var template = classes.findAll().stream().filter(c -> c.getCoach() != null && c.getRoom() != null && c.getSubject() != null).findFirst().orElseThrow();
        LocalDate start = LocalDate.now(VnpayService.OFFSET).plusDays(30);
        var course = classService.createClass(CreateClassRequest.builder().name("SePay rollback test " + UUID.randomUUID())
                .subjectId(template.getSubject().getId()).coachId(template.getCoach().getId()).roomId(template.getRoom().getId())
                .status("Active").maxCapacity(1).price(new BigDecimal("100000"))
                .startDate(start).durationWeeks(2).daysOfWeek(List.of(ClassSchedule.code(start.getDayOfWeek())))
                .slotId(3).startTime(LocalTime.of(12, 30)).endTime(LocalTime.of(13, 30)).build());
        int classId = course.getData().getId();
        String result = mvc.perform(post("/api/member/classes/{id}/register", classId).with(user(String.valueOf(memberId)).roles("Member"))
                .contentType("application/json").content("{\"paymentMethod\":\"DIRECT\",\"paymentChannel\":\"E_Wallet\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paymentStatus").value("Pending"))
                .andExpect(jsonPath("$.sessionCount").value(2)).andReturn().getResponse().getContentAsString();
        int paymentId = json.readTree(result).get("paymentId").asInt();
        String code = "SWP" + paymentId;
        mvc.perform(get("/api/member/payments/{id}", paymentId).with(user(String.valueOf(memberId)).roles("Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qrPayment.paymentCode").value(code))
                .andExpect(jsonPath("$.qrPayment.testMode").value(true)).andExpect(jsonPath("$.paymentAmount").value(200000));
        long transactionId = Math.abs(UUID.randomUUID().getMostSignificantBits() >>> 1);
        Map<String, Object> event = new HashMap<>(Map.of("id", transactionId, "gateway", "MBBank", "accountNumber", "0123456789",
                "code", code, "content", code, "transferType", "in", "transferAmount", 200000));
        mvc.perform(post("/api/payments/sepay/webhook").contentType("application/json").content(json.writeValueAsString(event)))
                .andExpect(status().isUnauthorized());
        event.put("transferAmount", 199999);
        webhook(event);
        entityManager.flush();
        assertEquals("Pending", jdbc.queryForObject("SELECT Status FROM dbo.Payment WHERE Payment_ID=?", String.class, paymentId));
        event.put("transferAmount", 200000);
        webhook(event); webhook(event);
        entityManager.flush(); entityManager.clear();
        assertEquals("Paid", jdbc.queryForObject("SELECT Status FROM dbo.Payment WHERE Payment_ID=?", String.class, paymentId));
        assertEquals(transactionId, jdbc.queryForObject("SELECT Sepay_Transaction_ID FROM dbo.Payment WHERE Payment_ID=?", Long.class, paymentId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Class_Booking b JOIN dbo.Class_Session s ON b.Session_ID=s.Session_ID WHERE s.Class_ID=? AND b.Status=N'Booked'", Integer.class, classId));
        mvc.perform(get("/api/member/payments/{id}", paymentId).with(user(String.valueOf(memberId)).roles("Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("Paid"))
                .andExpect(jsonPath("$.requiresReview").value(false)).andExpect(jsonPath("$.qrPayment").isEmpty());
    }

    private void webhook(Map<String, Object> event) throws Exception {
        mvc.perform(post("/api/payments/sepay/webhook").header("Authorization", "Apikey integration-test-only-key-at-least-32-characters")
                .contentType("application/json").content(json.writeValueAsString(event)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }
}
