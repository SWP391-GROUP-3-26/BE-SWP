package com.swp391.beswp.service;

import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import com.swp391.beswp.dto.TrainingPlanDtos.*;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in integration checks use temporary fixtures and roll back all data. */
@EnabledIfEnvironmentVariable(named = "TRAINING_PLAN_DB_CHECK", matches = "true")
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class TrainingPlanSqlServerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ClassRepository classes;
    @Autowired ClassSessionRepository sessions;
    @Autowired ClassBookingRepository bookings;
    @Autowired TrainingPlanRepository plans;
    @Autowired PaymentRepository payments;
    @Autowired Clock paymentClock;
    @Autowired TrainingPlanService training;
    @MockitoBean MembershipLifecycleService lifecycle;
    @MockitoBean ClassCompletionService completion;
    @MockitoBean VnpayService gateway;
    User coach, member, outsider;
    ClassEntity classroom;
    LocalDate today;

    @BeforeEach void fixtures() {
        today = LocalDate.now(paymentClock);
        coach = person("Coach"); member = person("Member"); outsider = person("Member");
        Subject subject = Subject.builder().name("Training plan test " + UUID.randomUUID()).category("Sport").status("Active").build();
        Room room = Room.builder().name("TP " + UUID.randomUUID().toString().substring(0, 8)).maxCapacity(10).build();
        em.persist(subject); em.persist(room);
        classroom = classes.saveAndFlush(ClassEntity.builder().name("Training plan test").coach(coach).subject(subject).room(room)
                .maxCapacity(10).status("Active").date(today).endDate(ClassSchedule.endDate(today, 2)).durationWeeks(2)
                .daysOfWeek(ClassSchedule.code(today.getDayOfWeek())).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).price(BigDecimal.ZERO).build());
        ClassSession session = sessions.saveAndFlush(ClassSession.builder().classEntity(classroom).date(today)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).status("Scheduled").build());
        bookings.saveAndFlush(ClassBooking.builder().user(member).session(session).bookingDatetime(LocalDateTime.now(paymentClock)).status("Booked").build());
    }

    User person(String role) {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User person = new User(); person.setRole(roles.findByRoleNameIgnoreCase(role).orElseThrow());
        person.setUsername("tp_" + unique); person.setEmail(unique + "@example.test"); person.setPassword("test-only");
        person.setFullName("Kế hoạch " + role); person.setStatus("Active");
        return users.saveAndFlush(person);
    }

    @Test void packageQuotaCountsOnlyUnexpiredHoldsAndConfirmedPackageSessions() {
        LocalDateTime now = LocalDateTime.now(paymentClock).withNano(0);
        ClassBooking booking = bookings.findMyBookings(member.getId(), null, null).getFirst();
        booking.setPaymentMethod("PACKAGE"); booking.setStatus("Pending");
        booking.setBookingDatetime(now); booking.setPaymentExpiresAt(now.plusMinutes(15));
        em.flush();
        var start = today.atStartOfDay(); var end = today.atTime(23, 59, 59);
        assertEquals(1, bookings.countHeldPackageSessions(member.getId(), start, end, now));
        assertEquals(0, bookings.countUsedPackageSessions(member.getId(), start, end));
        assertEquals(0, bookings.countHeldPackageSessions(outsider.getId(), start, end, now));
        assertEquals(0, bookings.countHeldPackageSessions(member.getId(), start, end, now.plusMinutes(15)));
        booking.setPaymentMethod("DIRECT"); em.flush();
        assertEquals(0, bookings.countHeldPackageSessions(member.getId(), start, end, now));
        booking.setPaymentMethod("PACKAGE"); booking.setStatus("Booked"); booking.setPaymentExpiresAt(null); em.flush();
        assertEquals(0, bookings.countHeldPackageSessions(member.getId(), start, end, now));
        assertEquals(1, bookings.countUsedPackageSessions(member.getId(), start, end));
        booking.setStatus("Cancelled"); em.flush();
        assertEquals(0, bookings.countUsedPackageSessions(member.getId(), start, end));
    }

    @Test void memberResumesSamePaymentWithOwnershipRoleAndExpiryChecks() throws Exception {
        LocalDateTime now = LocalDateTime.now(paymentClock).withNano(0);
        LocalDate start = today.plusDays(1);
        classroom.setDate(start); classroom.setEndDate(ClassSchedule.endDate(start, 2));
        classroom.setDaysOfWeek(ClassSchedule.code(start.getDayOfWeek()));
        ClassBooking booking = bookings.findMyBookings(member.getId(), null, null).getFirst();
        booking.getSession().setDate(start);
        booking.setStatus("Pending"); booking.setPaymentMethod("PACKAGE"); booking.setBookingDatetime(now);
        booking.setPaymentExpiresAt(now.plusMinutes(15)); booking.setRegistrationCode(UUID.randomUUID().toString());
        ClassSession second = sessions.saveAndFlush(ClassSession.builder().classEntity(classroom).date(start.plusDays(7))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).status("Scheduled").build());
        bookings.saveAndFlush(ClassBooking.builder().user(member).session(second).status("Pending").paymentMethod("DIRECT")
                .bookingDatetime(now).paymentExpiresAt(booking.getPaymentExpiresAt()).registrationCode(booking.getRegistrationCode()).build());
        Payment payment = payments.saveAndFlush(Payment.builder().booking(booking).paymentType("Class_Booking").method("Card")
                .amount(new BigDecimal("100000")).status("Pending").paymentDate(now).expiresAt(booking.getPaymentExpiresAt()).build());
        String url = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=" + payment.getId();
        org.mockito.Mockito.when(gateway.createPaymentUrl(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(url);
        String endpoint = "/api/member/payments/" + payment.getId() + "/resume";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(endpoint).with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.paymentId").value(payment.getId()))
                    .andExpect(jsonPath("$.paymentUrl").value(url)).andExpect(jsonPath("$.canResumePayment").value(true))
                    .andExpect(jsonPath("$.packageSessions").value(1)).andExpect(jsonPath("$.payableSessions").value(1));
        }
        assertEquals(1, payments.findAllByBooking_IdOrderByIdDesc(booking.getId()).size());
        assertEquals(now.plusMinutes(15), payment.getExpiresAt());
        assertEquals(1, bookings.countHeldPackageSessions(member.getId(), today.atStartOfDay(), classroom.getEndDate().atTime(23, 59, 59), now));
        mvc.perform(post(endpoint).with(user(outsider.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isNotFound());
        mvc.perform(post(endpoint).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach")))
                .andExpect(status().isForbidden());
        payment.setExpiresAt(now.minusMinutes(1)); em.flush();
        mvc.perform(post(endpoint).with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canResumePayment").value(false))
                .andExpect(jsonPath("$.paymentStatus").value("Failed"));
        assertEquals("Cancelled", booking.getStatus());
    }

    Map<String, Object> body(boolean publish, long version, Integer exerciseId) {
        Map<String, Object> exercise = new HashMap<>(Map.of("name", "Bơi kỹ thuật", "target", "200m", "instructions", "Chú ý nhịp thở"));
        if (exerciseId != null) exercise.put("id", exerciseId);
        return Map.of("classId", classroom.getId(), "memberId", member.getId(), "title", "Kế hoạch tuần đầu", "goal", "Cải thiện kỹ thuật",
                "startDate", today.toString(), "endDate", today.plusDays(7).toString(), "publish", publish, "version", version, "exercises", List.of(exercise));
    }

    JsonNode create(boolean publish) throws Exception {
        String response = mvc.perform(post("/api/coach/training-plans").with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(body(publish, 0, null))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Kế hoạch tuần đầu"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data");
    }

    @Test void assignReadRecordEditAndCompleteRoundTrip() throws Exception {
        JsonNode plan = create(true);
        int id = plan.path("id").asInt(), exerciseId = plan.path("exercises").get(0).path("id").asInt();
        assertTrue(plan.path("unread").asBoolean());
        mvc.perform(get("/api/member/training-plans").with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
        String read = mvc.perform(post("/api/member/training-plans/" + id + "/read?revision=1").with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(false)).andReturn().getResponse().getContentAsString();
        long version = json.readTree(read).path("data").path("version").asLong();
        String recorded = mvc.perform(put("/api/member/training-plans/" + id + "/exercises/" + exerciseId + "/result")
                .with(user(member.getId().toString()).authorities(() -> "ROLE_Member")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("completed", true, "memberResult", "Đã bơi đủ", "version", version))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completedExercises").value(1)).andReturn().getResponse().getContentAsString();
        version = json.readTree(recorded).path("data").path("version").asLong();
        String edited = mvc.perform(put("/api/coach/training-plans/" + id).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(body(true, version, exerciseId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unread").value(true))
                .andExpect(jsonPath("$.data.exercises[0].memberResult").value("Đã bơi đủ"))
                .andReturn().getResponse().getContentAsString();
        version = json.readTree(edited).path("data").path("version").asLong();
        mvc.perform(patch("/api/coach/training-plans/" + id + "/review").with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("status", "COMPLETED", "coachFeedback", "Tiến bộ tốt", "version", version))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.canRecord").value(false));
        em.flush(); em.clear();
        TrainingPlan persisted = plans.findById(id).orElseThrow();
        assertEquals(2, persisted.getPublishedRevision()); assertEquals(1, persisted.getReadRevision());
        assertEquals("Tiến bộ tốt", persisted.getCoachFeedback()); assertTrue(persisted.getExercises().getFirst().isCompleted());
    }

    @Test void draftPrivacyAndPayloadValidation() throws Exception {
        int id = create(false).path("id").asInt();
        mvc.perform(get("/api/member/training-plans/" + id).with(user(member.getId().toString()).authorities(() -> "ROLE_Member"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/coach/training-plans/" + id).with(user(member.getId().toString()).authorities(() -> "ROLE_Member"))).andExpect(status().isForbidden());
        Map<String, Object> invalid = new HashMap<>(body(true, 0, null)); invalid.put("exercises", List.of());
        mvc.perform(post("/api/coach/training-plans").with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
    }

    @Test void rejectsForeignMemberAndStaleVersion() throws Exception {
        JsonNode plan = create(true);
        int id = plan.path("id").asInt();
        mvc.perform(get("/api/member/training-plans/" + id).with(user(outsider.getId().toString()).authorities(() -> "ROLE_Member"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/member/training-plans/" + id + "/read?revision=1").with(user(member.getId().toString()).authorities(() -> "ROLE_Member"))).andExpect(status().isOk());
        mvc.perform(put("/api/coach/training-plans/" + id).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(body(true, 0, plan.path("exercises").get(0).path("id").asInt()))))
                .andExpect(status().isConflict());
    }

    org.springframework.security.core.Authentication actor(User user, String role) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user.getId().toString(), "",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test void classPlanPersistsPrivateResultsReadStateAndNewRecipients() throws Exception {
        // A personal assignment and a shared class assignment may coexist.
        create(true);
        Map<String, Object> input = new HashMap<>(body(true, 0, null));
        input.remove("memberId"); input.put("scope", "CLASS");
        String response = mvc.perform(post("/api/coach/training-plans")
                .with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scope").value("CLASS"))
                .andExpect(jsonPath("$.data.recipientCount").value(1)).andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(response).path("data");
        int id = created.path("id").asInt(), exerciseId = created.path("exercises").get(0).path("id").asInt();
        var coachAuth = actor(coach, "Coach"); var memberAuth = actor(member, "Member"); var outsiderAuth = actor(outsider, "Member");
        assertTrue(training.memberPlans(memberAuth).stream().anyMatch(p -> p.id().equals(id)));
        assertTrue(training.memberPlans(outsiderAuth).isEmpty());
        var read = training.markRead(memberAuth, id, 1);
        var recorded = training.recordResult(memberAuth, id, exerciseId, new ExerciseResult(true, "Kết quả thành viên A", read.version()));
        ClassSession session = sessions.findByClassEntity_Id(classroom.getId()).getFirst();
        bookings.saveAndFlush(ClassBooking.builder().user(outsider).session(session).status("Booked").bookingDatetime(LocalDateTime.now(paymentClock)).build());
        assertTrue(training.memberPlans(outsiderAuth).isEmpty(), "Joining the class does not silently add a recipient");
        var added = training.addRecipients(coachAuth, id, new VersionInput(recorded.version()));
        assertEquals(2, added.recipientCount());
        var second = training.detail(outsiderAuth, id, false);
        assertTrue(second.unread()); assertEquals(0, second.completedExercises()); assertTrue(second.recipients().isEmpty());
        assertEquals("", second.exercises().getFirst().memberResult());
        var reviewed = training.reviewRecipient(coachAuth, id, member.getId(), new RecipientFeedback("Nhận xét chỉ cho A", added.version()));
        assertEquals("Nhận xét chỉ cho A", training.detail(memberAuth, id, false).personalFeedback());
        assertNull(training.detail(outsiderAuth, id, false).personalFeedback());
        var edited = training.update(coachAuth, id, new SavePlan(classroom.getId(), null, "Cập nhật cả lớp", "Mục tiêu mới", "", today,
                today.plusDays(7), true, List.of(new ExerciseInput(exerciseId, today, "Bơi kỹ thuật", "", "300m")), reviewed.version(), "CLASS"));
        em.flush(); em.clear();
        assertTrue(training.detail(memberAuth, id, false).unread());
        assertEquals("Kết quả thành viên A", training.detail(memberAuth, id, false).exercises().getFirst().memberResult());
        assertEquals(1, training.detail(coachAuth, id, true).completedExercises());
        training.review(coachAuth, id, new ReviewPlan("COMPLETED", "Hoàn tất", edited.version()));
        assertFalse(training.detail(memberAuth, id, false).canRecord());
        assertEquals(1, training.detail(memberAuth, id, false).completedExercises());
    }

    @Test void classAudienceValidationAndEligibilityUseRealBookings() throws Exception {
        mvc.perform(get("/api/coach/training-plans/eligible-members?classId=" + classroom.getId())
                .with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].memberId").value(member.getId()));
        Map<String, Object> invalid = new HashMap<>(body(true, 0, null)); invalid.put("scope", "CLASS");
        mvc.perform(post("/api/coach/training-plans").with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
    }
}
