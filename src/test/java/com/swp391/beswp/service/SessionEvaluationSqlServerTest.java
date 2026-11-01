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

@EnabledIfEnvironmentVariable(named = "SESSION_EVALUATION_DB_CHECK", matches = "true")
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class SessionEvaluationSqlServerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ClassRepository classes;
    @Autowired ClassSessionRepository sessions;
    @Autowired ClassBookingRepository bookings;
    @Autowired CoachAttendanceRepository attendance;
    @Autowired TrainingResultRepository results;
    @Autowired Clock paymentClock;
    @MockitoBean MembershipLifecycleService lifecycle;
    @MockitoBean ClassCompletionService completion;
    User coach, member, other, foreignCoach;
    ClassEntity classroom;
    ClassSession session;

    @BeforeEach void fixtures() {
        coach = person("Coach"); member = person("Member"); other = person("Member"); foreignCoach = person("Coach");
        Subject subject = Subject.builder().name("Evaluation " + UUID.randomUUID()).category("Sport").status("Active").build();
        Room room = Room.builder().name("EV " + UUID.randomUUID().toString().substring(0, 8)).maxCapacity(10).build();
        em.persist(subject); em.persist(room);
        LocalDate yesterday = LocalDate.now(paymentClock).minusDays(1);
        classroom = classes.saveAndFlush(ClassEntity.builder().name("Evaluation test").coach(coach).subject(subject).room(room)
                .maxCapacity(10).status("Active").date(yesterday).endDate(ClassSchedule.endDate(yesterday, 2)).durationWeeks(2)
                .daysOfWeek(ClassSchedule.code(yesterday.getDayOfWeek())).startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(9, 0)).price(BigDecimal.ZERO).build());
        session = sessions.saveAndFlush(ClassSession.builder().classEntity(classroom).date(yesterday)
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(9, 0)).status("Completed").build());
        for (User student : List.of(member, other)) {
            bookings.saveAndFlush(ClassBooking.builder().user(student).session(session).bookingDatetime(LocalDateTime.now(paymentClock)).status("Completed").build());
            attendance.saveAndFlush(AttendanceRecord.builder().session(session).user(student).status("Present").build());
        }
    }

    User person(String role) {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User person = new User(); person.setRole(roles.findByRoleNameIgnoreCase(role).orElseThrow());
        person.setUsername("ev_" + unique); person.setEmail(unique + "@example.test"); person.setPassword("test-only");
        person.setFullName("Evaluation " + role + unique); person.setStatus("Active"); return users.saveAndFlush(person);
    }

    String root() { return "/api/coach/classes/" + classroom.getId() + "/sessions/" + session.getId() + "/evaluations"; }
    Map<String, Object> body(Long version) {
        Map<String, Object> body = new HashMap<>(Map.of("trainingContent", "Bơi tự do", "result", "200m", "progressStatus", "IMPROVING",
                "coachFeedback", "Nhịp thở tốt hơn", "nextSteps", "Giữ nhịp ổn định"));
        if (version != null) body.put("version", version); return body;
    }
    void save(User student, Long version) throws Exception {
        mvc.perform(put(root() + "/" + student.getId()).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(body(version)))).andExpect(status().isOk());
    }

    @Test void persistsResultsAndShowsOnlyTheCallingMembersEvaluation() throws Exception {
        save(member, null); em.flush(); em.clear();
        TrainingResult saved = results.findBySession_IdAndMember_Id(session.getId(), member.getId()).orElseThrow();
        assertEquals("200m", saved.getResult()); assertEquals(coach.getId(), saved.getCoach().getId());
        mvc.perform(get("/api/member/session-evaluations").with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].sessionId").value(session.getId()));
        mvc.perform(get("/api/member/session-evaluations/" + session.getId()).with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.result").value("200m"))
                .andExpect(jsonPath("$.data.memberId").value(member.getId()));
        mvc.perform(get("/api/member/session-evaluations/" + session.getId()).with(user(other.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isNotFound());
        long originalVersion = saved.getVersion();
        save(member, originalVersion); em.flush(); em.clear();
        assertEquals(1, results.findBySession_ClassEntity_IdOrderBySession_DateDescSession_EndTimeDescIdDesc(classroom.getId()).size());
        assertTrue(results.findBySession_IdAndMember_Id(session.getId(), member.getId()).orElseThrow().getVersion() > originalVersion);
        mvc.perform(put(root() + "/" + member.getId()).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(body(null)))).andExpect(status().isConflict());
    }

    @Test void blocksAttendanceContradictionAndSupportsWithdrawalWithoutDeletingHistory() throws Exception {
        save(member, null);
        String attendanceUrl = "/api/coach/classes/" + classroom.getId() + "/sessions/" + session.getId() + "/attendance";
        Map<String, Object> marks = Map.of("records", List.of(Map.of("memberId", member.getId(), "status", "Absent"), Map.of("memberId", other.getId(), "status", "Present")));
        mvc.perform(put(attendanceUrl).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(marks))).andExpect(status().isConflict());
        TrainingResult result = results.findBySession_IdAndMember_Id(session.getId(), member.getId()).orElseThrow();
        mvc.perform(post(root() + "/" + member.getId() + "/withdraw").with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(Map.of("version", result.getVersion())))).andExpect(status().isOk());
        mvc.perform(get("/api/member/session-evaluations").with(user(member.getId().toString()).authorities(() -> "ROLE_Member")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(put(attendanceUrl).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(marks))).andExpect(status().isOk());
        em.flush(); em.clear();
        result = results.findBySession_IdAndMember_Id(session.getId(), member.getId()).orElseThrow();
        assertTrue(result.isWithdrawn()); assertEquals("200m", result.getResult());
    }

    @Test void validatesPayloadRoleOwnershipSavedAttendanceAndSessionEnd() throws Exception {
        mvc.perform(get(root()).with(user(foreignCoach.getId().toString()).authorities(() -> "ROLE_Coach"))).andExpect(status().isForbidden());
        mvc.perform(put(root() + "/" + member.getId()).with(user(member.getId().toString()).authorities(() -> "ROLE_Member"))
                .contentType("application/json").content(json.writeValueAsString(body(null)))).andExpect(status().isForbidden());
        Map<String, Object> invalid = body(null); invalid.put("progressStatus", "MADE_UP");
        mvc.perform(put(root() + "/" + member.getId()).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        AttendanceRecord mark = attendance.findBySession_Id(session.getId()).stream().filter(a -> a.getUser().getId().equals(member.getId())).findFirst().orElseThrow();
        mark.setStatus("Absent"); attendance.flush();
        mvc.perform(put(root() + "/" + member.getId()).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(body(null)))).andExpect(status().isConflict());
        mark.setStatus("Present"); session.setDate(LocalDate.now(paymentClock).plusDays(1)); sessions.flush();
        mvc.perform(put(root() + "/" + member.getId()).with(user(coach.getId().toString()).authorities(() -> "ROLE_Coach"))
                .contentType("application/json").content(json.writeValueAsString(body(null)))).andExpect(status().isConflict());
    }
}
