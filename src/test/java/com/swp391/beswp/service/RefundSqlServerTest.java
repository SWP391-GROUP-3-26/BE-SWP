package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** New fixtures and all receipt/refund mutations roll back. No bank or gateway requests. */
@EnabledIfEnvironmentVariable(named = "REFUND_DB_CHECK", matches = "true")
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class RefundSqlServerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ClassRepository classes;
    @Autowired ClassSessionRepository sessions;
    @Autowired ClassBookingRepository bookings;
    @Autowired PaymentRepository payments;
    @Autowired RefundRequestRepository refunds;
    @Autowired RefundService refundService;
    @Autowired Clock paymentClock;
    @MockitoBean MembershipLifecycleService lifecycle;
    @MockitoBean ClassCompletionService completion;
    User member, manager, cashier, outsider;
    ClassEntity classroom;
    List<ClassBooking> registration;
    Payment payment;
    LocalDate today;

    @BeforeEach void fixtures() {
        today = LocalDate.now(paymentClock);
        member = person("Member"); manager = person("Center Manager"); cashier = person("Receptionist"); outsider = person("Member");
        Subject subject = Subject.builder().name("Refund " + UUID.randomUUID()).category("Sport").status("Active").build();
        Room room = Room.builder().name("RF " + UUID.randomUUID().toString().substring(0, 8)).maxCapacity(10).build();
        em.persist(subject); em.persist(room);
        LocalDate start = today.minusDays(3);
        classroom = classes.saveAndFlush(ClassEntity.builder().name("Lớp tại lúc mua").coach(person("Coach")).subject(subject).room(room)
                .maxCapacity(10).status("Active").date(start).durationWeeks(2).endDate(ClassSchedule.endDate(start, 2))
                .daysOfWeek(ClassSchedule.code(start.getDayOfWeek())).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).price(BigDecimal.TEN).build());
        registration = new ArrayList<>(); String code = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            ClassSession session = sessions.saveAndFlush(ClassSession.builder().classEntity(classroom).date(today.plusDays(i == 0 ? -1 : i))
                    .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).status(i == 0 ? "Completed" : "Scheduled").build());
            registration.add(bookings.saveAndFlush(ClassBooking.builder().user(member).session(session).status(i == 0 ? "Completed" : "Booked")
                    .paymentMethod("DIRECT").bookingDatetime(LocalDateTime.now(paymentClock).minusDays(4)).registrationCode(code).build()));
        }
        payment = payments.saveAndFlush(Payment.builder().booking(registration.getFirst()).paymentType("Class_Booking").method("Cash")
                .amount(new BigDecimal("1000000")).discountAmount(new BigDecimal("200000")).status("Paid").paymentDate(LocalDateTime.now(paymentClock).minusDays(4)).build());
    }
    User person(String role) {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User u = new User(); u.setUsername("rf_" + unique); u.setEmail(unique + "@example.test"); u.setFullName("Test " + role);
        u.setRole(roles.findByRoleNameIgnoreCase(role).orElseThrow()); u.setStatus("Active"); u.setPassword("test-only"); return users.saveAndFlush(u);
    }
    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(User u) {
        return user(u.getId().toString()).authorities(() -> "ROLE_" + u.getRole().getRoleName());
    }
    JsonNode data(String body) { return json.readTree(body).path("data"); }
    JsonNode cancel() throws Exception {
        int bookingId = registration.getFirst().getId();
        mvc.perform(get("/api/member/classes/bookings/" + bookingId + "/cancel-preview").with(as(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedRefund").value(666666))
                .andExpect(jsonPath("$.data.cancelSessions").value(2));
        mvc.perform(post("/api/member/classes/bookings/" + bookingId + "/cancel").with(as(member)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("estimatedRefund", 1, "cancelSessions", 2)))).andExpect(status().isConflict());
        mvc.perform(post("/api/member/classes/bookings/" + bookingId + "/cancel").with(as(member)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("estimatedRefund", 666666, "cancelSessions", 2)))).andExpect(status().isOk());
        return data(mvc.perform(get("/api/member/payment-history/" + payment.getId()).with(as(member))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("refund");
    }

    @Test void fullRefundFlowPreservesReceiptAndRejectsDuplicatePayout() throws Exception {
        JsonNode r = cancel(); int id = r.path("id").asInt(); long version = r.path("version").asLong();
        assertEquals("PENDING", r.path("status").asText());
        assertEquals(List.of("Completed", "Cancelled", "Cancelled"), registration.stream().map(ClassBooking::getStatus).toList());
        assertEquals(3, r.path("sessions").size());
        mvc.perform(post("/api/receptionist/refunds/" + id + "/payout").with(as(cashier)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("pickupCode", "WRONG", "version", version)))).andExpect(status().isConflict());
        mvc.perform(put("/api/member/refunds/" + id + "/recipient").with(as(member)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("method", "BANK", "version", version)))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/member/refunds/" + id + "/recipient").with(as(member)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("method", "CASH", "version", version)))).andExpect(status().isConflict());
        String approved = mvc.perform(patch("/api/center-manager/refunds/" + id + "/review").with(as(manager)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("status", "APPROVED", "approvedAmount", 666666, "note", "Theo số buổi hủy", "version", version))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        version = data(approved).path("version").asLong();
        String recipient = mvc.perform(put("/api/member/refunds/" + id + "/recipient").with(as(member)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("method", "CASH", "version", version))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String pickupCode = data(recipient).path("pickupCode").asText(); assertTrue(pickupCode.startsWith("HT-"));
        mvc.perform(get("/api/receptionist/refunds/" + id).with(as(cashier))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickupCode").doesNotExist()).andExpect(jsonPath("$.data.pickupReady").value(true));
        mvc.perform(get("/api/center-manager/refunds/" + id).with(as(manager))).andExpect(status().isOk()).andExpect(jsonPath("$.data.pickupCode").doesNotExist());
        mvc.perform(get("/api/member/payment-history/" + payment.getId()).with(as(member))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refund.pickupCode").value(pickupCode));
        version = data(recipient).path("version").asLong();
        mvc.perform(post("/api/receptionist/refunds/" + id + "/payout").with(as(cashier)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("pickupCode", "WRONG", "version", version)))).andExpect(status().isBadRequest());
        assertEquals("APPROVED", refunds.findById(id).orElseThrow().getStatus());
        String payoutBody = json.writeValueAsString(Map.of("pickupCode", pickupCode, "note", "Rollback-only test; no cash paid", "version", version));
        mvc.perform(post("/api/receptionist/refunds/" + id + "/payout").with(as(cashier)).contentType("application/json").content(payoutBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.payoutReference").value("CASH-HT-" + id)).andExpect(jsonPath("$.data.pickupCode").doesNotExist());
        mvc.perform(post("/api/receptionist/refunds/" + id + "/payout").with(as(cashier)).contentType("application/json").content(payoutBody)).andExpect(status().isConflict());
        classroom.setName("Lớp đổi tên sau mua"); em.flush(); em.clear();
        mvc.perform(get("/api/member/payment-history/" + payment.getId()).with(as(member))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("Paid")).andExpect(jsonPath("$.data.itemName").value("Lớp tại lúc mua"))
                .andExpect(jsonPath("$.data.refundedAmount").value(666666)).andExpect(jsonPath("$.data.retainedAmount").value(333334));
        assertNotNull(refunds.findById(id).orElseThrow().getPaidAt());
    }

    @Test void databaseRejectsDuplicatePickupCodesAcrossPayments() throws Exception {
        JsonNode first = cancel(); int id = first.path("id").asInt();
        mvc.perform(put("/api/member/refunds/" + id + "/recipient").with(as(member)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("method", "CASH", "version", first.path("version").asLong())))).andExpect(status().isOk());
        String code = refunds.findById(id).orElseThrow().getPickupCode();
        Payment other = payments.saveAndFlush(Payment.builder().booking(registration.getFirst()).paymentType("Class_Booking").method("Cash")
                .amount(new BigDecimal("1000000")).status("Paid").build());
        refundService.createForCancellation(other, registration, Set.of(registration.get(1).getId(), registration.get(2).getId()), member);
        RefundRequest second = refunds.findByPayment_Id(other.getId()).orElseThrow(); second.setPickupCode(code);
        var duplicate = assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> refunds.saveAndFlush(second));
        assertTrue(duplicate.getMostSpecificCause().getMessage().contains("UX_Refund_Pickup_Code"));
    }

    @Test void protectsOwnershipRolesAndStaleVersions() throws Exception {
        JsonNode r = cancel(); int id = r.path("id").asInt();
        mvc.perform(get("/api/member/payment-history/" + payment.getId()).with(as(outsider))).andExpect(status().isNotFound());
        mvc.perform(put("/api/member/refunds/" + id + "/recipient").with(as(outsider)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("method", "CASH", "version", 0)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/center-manager/refunds").with(as(member))).andExpect(status().isForbidden());
        mvc.perform(post("/api/member/refunds/" + id + "/read?revision=1").with(as(member))).andExpect(status().isOk());
        mvc.perform(patch("/api/center-manager/refunds/" + id + "/review").with(as(manager)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("status", "APPROVED", "approvedAmount", 666666, "version", 0)))).andExpect(status().isConflict());
    }

    @Test void historyIncludesClassAndPackagePaymentsAndSupportsFilters() throws Exception {
        MembershipPackage pack = MembershipPackage.builder().name("Refund history package " + UUID.randomUUID()).price(new BigDecimal("100000"))
                .discount(BigDecimal.ZERO).duration(30).includedClasses(10).status("Active").build(); em.persist(pack);
        MemberSubscription sub = MemberSubscription.builder().user(member).membershipPackage(pack).packageNameAtPurchase(pack.getName())
                .priceAtPurchase(pack.getPrice()).durationAtPurchase(30).startDate(today).endDate(today.plusDays(29)).status("Active").build(); em.persist(sub);
        payments.saveAndFlush(Payment.builder().subscription(sub).paymentType("Subscription").method("Cash").amount(pack.getPrice()).status("Paid").build());
        mvc.perform(get("/api/member/payment-history").with(as(member))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/member/payment-history?type=Subscription&status=Paid").with(as(member))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/member/payment-history?from=" + today.plusDays(1)).with(as(member))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void managerCanRecoverOldCancelledPaymentWithoutDuplicateRequest() throws Exception {
        registration.subList(1, 3).forEach(b -> b.setStatus("Cancelled")); payment.setRequiresReview(true); em.flush();
        String created = mvc.perform(post("/api/center-manager/refunds/payments/" + payment.getId()).with(as(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedAmount").value(666666)).andReturn().getResponse().getContentAsString();
        int id = data(created).path("id").asInt();
        mvc.perform(post("/api/center-manager/refunds/payments/" + payment.getId()).with(as(manager))).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
    }
}
