package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

/** Opt-in SQL Server check. New fixtures and all callbacks roll back; no real gateway requests. */
@EnabledIfEnvironmentVariable(named = "MEMBER_PACKAGE_DB_CHECK", matches = "true")
@SpringBootTest(properties = {"spring.jpa.show-sql=false", "app.sepay.enabled=true", "app.sepay.test-mode=true",
        "app.sepay.bank-code=MBBank", "app.sepay.account-number=0123456789", "app.sepay.account-name=TEST ONLY",
        "app.sepay.webhook-api-key=integration-test-only-key-at-least-32-characters",
        "app.vnpay.tmn-code=TESTCODE", "app.vnpay.hash-secret=integration-test-only-secret",
        "app.vnpay.return-url=http://localhost:8080/api/payments/vnpay/return", "app.vnpay.frontend-url=http://localhost:5173"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MemberPackageOnlineSqlServerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired MembershipPackageRepository packages;
    @Autowired MemberPackageSubscriptionRepository subscriptions;
    @Autowired PaymentRepository payments;
    @Autowired VnpayService vnpay;
    @Autowired EntityManager em;
    @Autowired Clock paymentClock;
    @MockitoBean MembershipLifecycleService lifecycle;
    User member;
    MembershipPackage pack;

    @BeforeEach void fixtures() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        member = new User(); member.setRole(roles.findByRoleNameIgnoreCase("Member").orElseThrow());
        member.setUsername("online_" + unique); member.setEmail(unique + "@example.test");
        member.setPassword("integration-only-no-login"); member.setFullName("Online package rollback test"); member.setStatus("Active");
        member = users.saveAndFlush(member);
        pack = packages.saveAndFlush(MembershipPackage.builder().name("Online rollback " + unique)
                .price(new BigDecimal("100000")).discount(new BigDecimal("10")).duration(30).includedClasses(12).status("Active").build());
    }

    Payment order(String method, String renewalStart, boolean renewal) throws Exception {
        var result = mvc.perform(post("/api/member/packages/" + (renewal ? "renewal" : "register"))
                .with(user(member.getId().toString()).authorities(() -> "ROLE_Member"))
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "packageId", pack.getId(), "paymentMethod", method, "renewalStart", renewalStart))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.paymentStatus").value("Pending"))
                .andExpect(jsonPath("$.data.subscriptionStatus").value("Pending"))
                .andReturn().getResponse().getContentAsString();
        int paymentId = json.readTree(result).path("data").path("paymentId").asInt();
        if ("Card".equals(method)) assertTrue(json.readTree(result).path("data").path("paymentUrl").asText().startsWith("https://sandbox.vnpayment.vn/"));
        em.flush();
        return payments.findById(paymentId).orElseThrow();
    }

    void sepay(Payment payment) throws Exception {
        mvc.perform(post("/api/payments/sepay/webhook").header("Authorization", "Apikey integration-test-only-key-at-least-32-characters")
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                        "id", 8000000000L + payment.getId(), "gateway", "MBBank", "accountNumber", "0123456789",
                        "code", "SWP" + payment.getId(), "content", "SWP" + payment.getId(), "transferType", "in", "transferAmount", payment.getAmount()))))
                .andExpect(status().isOk());
        em.flush(); em.refresh(payment); em.refresh(payment.getSubscription());
    }

    MemberSubscription current() {
        return subscriptions.saveAndFlush(MemberSubscription.builder().user(member).membershipPackage(pack)
                .packageNameAtPurchase(pack.getName()).priceAtPurchase(pack.getPrice()).durationAtPurchase(30)
                .startDate(LocalDate.now(paymentClock).minusDays(2)).endDate(LocalDate.now(paymentClock).plusDays(27)).status("Active").build());
    }

    @Test void qrPurchaseStaysUnusableUntilWebhookAndDuplicateIsHarmless() throws Exception {
        Payment payment = order("E_Wallet", "IMMEDIATE", false);
        assertFalse(subscriptions.findDuePendingSubscriptions(LocalDate.now(paymentClock)).stream()
                .anyMatch(s -> s.getId().equals(payment.getSubscription().getId())), "Unpaid subscription must be excluded from activation job");
        mvc.perform(get("/api/member/payments/" + payment.getId()).with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.qrPayment.paymentCode").value("SWP" + payment.getId()));
        sepay(payment); sepay(payment);
        assertEquals("Paid", payment.getStatus()); assertEquals("Active", payment.getSubscription().getStatus()); assertFalse(payment.isRequiresReview());
        mvc.perform(post("/api/member/packages/register").with(user(member.getId().toString()).authorities(() -> "ROLE_Member"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("packageId", pack.getId(), "paymentMethod", "Cash"))))
                .andExpect(status().isBadRequest());
    }

    @Test void immediateRenewalKeepsOldPackageUntilSignedVnpayCallback() throws Exception {
        var current = current();
        Payment payment = order("Card", "IMMEDIATE", true);
        assertEquals("Active", current.getStatus());
        var callback = new HashMap<>(Map.of("vnp_TmnCode", "TESTCODE", "vnp_TxnRef", payment.getId().toString(),
                "vnp_Amount", payment.getAmount().movePointRight(2).toBigIntegerExact().toString(), "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00", "vnp_TransactionNo", "123456", "vnp_PayDate", "20261026100000"));
        callback.put("vnp_SecureHash", vnpay.sign(callback));
        var request = get("/api/payments/vnpay/ipn"); callback.forEach(request::param);
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
        em.flush(); em.refresh(current); em.refresh(payment.getSubscription());
        assertEquals("Expired", current.getStatus()); assertEquals("Active", payment.getSubscription().getStatus());
    }

    @Test void scheduledRenewalIsPaidButWaitsForStartDate() throws Exception {
        var current = current();
        Payment payment = order("E_Wallet", "AFTER_CURRENT", true);
        sepay(payment); em.refresh(current);
        assertEquals("Paid", payment.getStatus()); assertFalse(payment.isRequiresReview());
        assertEquals("Pending", payment.getSubscription().getStatus()); assertEquals("Active", current.getStatus());
        assertTrue(subscriptions.findDuePendingSubscriptions(current.getEndDate().plusDays(1)).stream()
                .anyMatch(s -> s.getId().equals(payment.getSubscription().getId())));
        mvc.perform(get("/api/member/payments/" + payment.getId()).with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookingStatus").value("Chờ bắt đầu"));
    }
}
