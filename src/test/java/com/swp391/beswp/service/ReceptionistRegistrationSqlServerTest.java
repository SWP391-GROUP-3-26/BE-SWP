package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in SQL Server regression. Every fixture, payment and callback mutation rolls back; no external transfers or DDL. */
@EnabledIfSystemProperty(named = "receptionist.db.check", matches = "true")
@SpringBootTest(properties = {"spring.jpa.show-sql=false", "app.sepay.enabled=true", "app.sepay.test-mode=true",
        "app.sepay.bank-code=MBBank", "app.sepay.account-number=0123456789", "app.sepay.account-name=TEST ONLY",
        "app.sepay.webhook-api-key=integration-test-only-key-at-least-32-characters",
        "app.vnpay.tmn-code=TESTCODE", "app.vnpay.hash-secret=test-only-not-a-real-merchant-secret",
        "app.vnpay.return-url=http://localhost:8080/api/payments/vnpay/return", "app.vnpay.frontend-url=http://localhost:5173"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ReceptionistRegistrationSqlServerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired MembershipPackageRepository packages;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired VnpayService vnpay;
    @Autowired MemberPaymentService memberPayments;
    User member, actor;
    MembershipPackage pack;
    String base;

    User fixtureUser(String roleName) {
        User u = new User();
        u.setRole(roles.findAll().stream().filter(r -> roleName.equals(r.getRoleName())).findFirst().orElseThrow());
        String suffix = UUID.randomUUID().toString().replace("-", "");
        u.setUsername("test." + suffix); u.setEmail(suffix + "@example.test"); u.setFullName("Rollback " + roleName);
        u.setPassword("test-only-unused-hash"); u.setStatus("Active");
        return users.saveAndFlush(u);
    }

    @BeforeEach void fixture() {
        member = fixtureUser("Member"); actor = fixtureUser("Receptionist");
        pack = packages.saveAndFlush(MembershipPackage.builder().name("Rollback " + UUID.randomUUID()).price(new BigDecimal("100000"))
                .duration(30).includedClasses(10).status("Active").build());
        base = "/api/receptionist/registrations/members/" + member.getId();
    }

    int purchase(String method) throws Exception {
        var result = mvc.perform(post(base + "/packages").with(user(actor.getId().toString()).roles("Receptionist"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("packageId", pack.getId(), "paymentMethod", method))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("paymentId").asInt();
    }

    @Test void cashIsPaidForSelectedMemberAndProfileShowsQuota() throws Exception {
        int id = purchase("Cash"); em.flush();
        assertEquals("Paid", jdbc.queryForObject("SELECT Status FROM dbo.Payment WHERE Payment_ID=?", String.class, id));
        assertEquals(actor.getId(), jdbc.queryForObject("SELECT Receptionist_ID FROM dbo.Payment WHERE Payment_ID=?", Integer.class, id));
        assertEquals(member.getId(), jdbc.queryForObject("SELECT s.User_ID FROM dbo.Payment p JOIN dbo.Member_Subscription s ON p.Subscription_ID=s.Subscription_ID WHERE p.Payment_ID=?", Integer.class, id));
        mvc.perform(get("/api/receptionist/members/" + member.getId()).with(user(actor.getId().toString()).roles("Receptionist")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.activePackages[0].remainingClasses").value(10));
    }

    @Test void cancelledQrThenLateDuplicateWebhookRequiresReviewAndKeepsSubscriptionCancelled() throws Exception {
        int id = purchase("E_Wallet"); em.flush();
        mvc.perform(get(base + "/payments/" + id).with(user(actor.getId().toString()).roles("Receptionist")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("Pending"))
                .andExpect(jsonPath("$.memberId").value(member.getId())).andExpect(jsonPath("$.qrPayment.paymentCode").value("SWP" + id));
        mvc.perform(post(base + "/payments/" + id + "/cancel").with(user(actor.getId().toString()).roles("Receptionist")))
                .andExpect(status().isOk()); em.flush();
        String payload = json.writeValueAsString(Map.of("id", UUID.randomUUID().getMostSignificantBits() >>> 1, "gateway", "MBBank",
                "accountNumber", "0123456789", "code", "SWP" + id, "content", "SWP" + id, "transferType", "in", "transferAmount", 100000));
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/payments/sepay/webhook")
                .header("Authorization", "Apikey integration-test-only-key-at-least-32-characters")
                .contentType("application/json").content(payload)).andExpect(status().isOk());
        em.flush(); em.clear();
        mvc.perform(get(base + "/payments/" + id).with(user(actor.getId().toString()).roles("Receptionist")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("Paid"))
                .andExpect(jsonPath("$.requiresReview").value(true)).andExpect(jsonPath("$.bookingStatus").value("Đã hủy"));
    }

    @Test void signedVnpaySubscriptionCallbackIsIdempotentAndReturnKeepsMemberContext() throws Exception {
        int id = purchase("Card"); em.flush();
        Map<String, String> params = new HashMap<>(Map.of("vnp_TmnCode", "TESTCODE", "vnp_TxnRef", String.valueOf(id),
                "vnp_Amount", "10000000", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "123456", "vnp_PayDate", "20261020070000"));
        params.put("vnp_SecureHash", vnpay.sign(params));
        assertTrue(memberPayments.returnUrl(params).contains("/receptionist/members/" + member.getId() + "/payment-result"));
        assertEquals("00", memberPayments.processIpn(params).get("RspCode"));
        assertEquals("02", memberPayments.processIpn(params).get("RspCode")); em.flush(); em.clear();
        mvc.perform(get(base + "/payments/" + id).with(user(actor.getId().toString()).roles("Receptionist")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("Paid"))
                .andExpect(jsonPath("$.requiresReview").value(false)).andExpect(jsonPath("$.bookingStatus").value("Đang diễn ra"));
    }
}
