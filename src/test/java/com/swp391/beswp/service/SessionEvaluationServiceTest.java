package com.swp391.beswp.service;

import com.swp391.beswp.dto.SessionEvaluationDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SessionEvaluationServiceTest {
    TrainingResultRepository results = mock(TrainingResultRepository.class);
    ClassRepository classes = mock(ClassRepository.class);
    ClassSessionRepository sessions = mock(ClassSessionRepository.class);
    CoachAttendanceRepository attendance = mock(CoachAttendanceRepository.class);
    UserRepository users = mock(UserRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    SessionEvaluationService service = new SessionEvaluationService(results, classes, sessions, attendance, users, clock);
    List<TrainingResult> stored = new ArrayList<>();
    User coach, member, other;
    ClassEntity classroom;
    ClassSession session;

    @BeforeEach void setup() {
        coach = person(3, "Coach"); member = person(4, "Member"); other = person(5, "Member");
        classroom = ClassEntity.builder().id(10).name("Bơi cơ bản").coach(coach).status("Active")
                .date(LocalDate.of(2026, 10, 20)).endDate(LocalDate.of(2026, 11, 20)).build();
        session = ClassSession.builder().id(20).classEntity(classroom).date(LocalDate.of(2026, 10, 30))
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(9, 0)).status("Scheduled").build();
        when(classes.findById(10)).thenReturn(Optional.of(classroom));
        when(classes.findByIdForUpdate(10)).thenReturn(Optional.of(classroom));
        when(sessions.findById(20)).thenReturn(Optional.of(session));
        when(attendance.findRegisteredMembersForSession(20)).thenReturn(List.of(member, other));
        when(attendance.findBySession_Id(20)).thenReturn(List.of(mark(member, "Present"), mark(other, "Absent")));
        when(results.findBySession_ClassEntity_IdOrderBySession_DateDescSession_EndTimeDescIdDesc(10)).thenAnswer(call -> stored);
        when(results.findBySession_IdAndMember_Id(anyInt(), anyInt())).thenAnswer(call -> stored.stream()
                .filter(r -> r.getSession().getId().equals(call.getArgument(0)) && r.getMember().getId().equals(call.getArgument(1))).findFirst());
        when(results.findByMember_IdAndWithdrawnFalseOrderByUpdatedAtDesc(anyInt())).thenAnswer(call -> stored.stream()
                .filter(r -> r.getMember().getId().equals(call.getArgument(0)) && !r.isWithdrawn()).toList());
        when(results.saveAndFlush(any())).thenAnswer(call -> {
            TrainingResult r = call.getArgument(0);
            if (r.getId() == null) { r.setId(stored.size() + 1); stored.add(r); }
            return r;
        });
    }
    User person(int id, String roleName) {
        Role role = new Role(); role.setRoleName(roleName);
        User user = new User(); user.setId(id); user.setRole(role); user.setFullName(roleName + id); user.setStatus("Active");
        when(users.findById(id)).thenReturn(Optional.of(user)); return user;
    }
    AttendanceRecord mark(User member, String status) { return AttendanceRecord.builder().session(session).user(member).status(status).build(); }
    Authentication auth(int id, String role) { return new UsernamePasswordAuthenticationToken("" + id, "", List.of(new SimpleGrantedAuthority("ROLE_" + role))); }
    SaveEvaluation input(Long version) { return new SaveEvaluation(" Bơi kỹ thuật ", " 200m ", "IMPROVING", " Giữ nhịp tốt hơn ", " Tập thở ", version); }
    void status(HttpStatus expected, Runnable operation) { assertEquals(expected, assertThrows(ResponseStatusException.class, operation::run).getStatusCode()); }

    @Test void savesOneEvaluationPerMemberWithCoachAttributionAndTrimmedText() {
        var data = service.save(auth(3, "Coach"), 10, 20, 4, input(null));
        assertTrue(data.canEvaluate()); assertEquals(2, data.students().size());
        assertEquals("200m", data.students().getFirst().evaluation().result());
        assertEquals("Bơi kỹ thuật", stored.getFirst().getTrainingContent());
        assertEquals(coach, stored.getFirst().getCoach()); assertEquals(session.getDate(), stored.getFirst().getDate());
        service.save(auth(3, "Coach"), 10, 20, 4, input(0L)); assertEquals(1, stored.size());
    }

    @Test void refusesAbsentUnmarkedAndUnregisteredStudents() {
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 5, input(null)));
        when(attendance.findBySession_Id(20)).thenReturn(List.of());
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(null)));
        status(HttpStatus.NOT_FOUND, () -> service.save(auth(3, "Coach"), 10, 20, 99, input(null)));
        verify(results, never()).saveAndFlush(any());
    }

    @Test void enforcesEndTimeAndCancelledSessionsWithoutWaitingForStatusJob() {
        session.setEndTime(LocalTime.of(10, 1));
        assertFalse(service.coachView(auth(3, "Coach"), 10, 20).canEvaluate());
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(null)));
        session.setEndTime(LocalTime.of(10, 0));
        service.save(auth(3, "Coach"), 10, 20, 4, input(null));
        session.setStatus("Cancelled");
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(0L)));
        session.setStatus("Completed"); classroom.setStatus("Cancelled");
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(0L)));
    }

    @Test void completedClassesStillAllowEvaluationOfTheirPastSessions() {
        classroom.setStatus("Completed");
        assertTrue(service.save(auth(3, "Coach"), 10, 20, 4, input(null)).canEvaluate());
        session.setDate(classroom.getDate().minusDays(1));
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(0L)));
    }

    @Test void rejectsUnauthorizedCoachesWrongRolesAndInactiveAccounts() {
        classroom.setCoach(person(9, "Coach"));
        status(HttpStatus.FORBIDDEN, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(null)));
        status(HttpStatus.FORBIDDEN, () -> service.coachView(auth(4, "Member"), 10, 20));
        status(HttpStatus.UNAUTHORIZED, () -> service.memberSummaries(null));
        coach.setStatus("Locked");
        status(HttpStatus.FORBIDDEN, () -> service.coachView(auth(3, "Coach"), 10, 20));
    }

    @Test void rejectsForeignSessionsAndConcurrentCreationOrStaleUpdates() {
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(0L)));
        service.save(auth(3, "Coach"), 10, 20, 4, input(null));
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(null)));
        stored.getFirst().setVersion(1);
        status(HttpStatus.CONFLICT, () -> service.save(auth(3, "Coach"), 10, 20, 4, input(0L)));
        status(HttpStatus.CONFLICT, () -> service.withdraw(auth(3, "Coach"), 10, 20, 4, new WithdrawEvaluation(0L)));
        session.setClassEntity(ClassEntity.builder().id(99).build());
        status(HttpStatus.NOT_FOUND, () -> service.coachView(auth(3, "Coach"), 10, 20));
    }

    @Test void membersSeeOnlyTheirOwnEvaluationAndWithdrawalPreservesDataForCorrection() {
        service.save(auth(3, "Coach"), 10, 20, 4, input(null));
        assertEquals(1, service.memberSummaries(auth(4, "Member")).size());
        assertEquals("200m", service.memberDetail(auth(4, "Member"), 20).result());
        assertTrue(service.memberSummaries(auth(5, "Member")).isEmpty());
        status(HttpStatus.NOT_FOUND, () -> service.memberDetail(auth(5, "Member"), 20));
        service.withdraw(auth(3, "Coach"), 10, 20, 4, new WithdrawEvaluation(0L));
        assertEquals(1, stored.size()); assertTrue(stored.getFirst().isWithdrawn());
        assertTrue(service.memberSummaries(auth(4, "Member")).isEmpty());
        status(HttpStatus.NOT_FOUND, () -> service.memberDetail(auth(4, "Member"), 20));
        service.save(auth(3, "Coach"), 10, 20, 4, input(0L));
        assertFalse(stored.getFirst().isWithdrawn()); assertEquals(1, stored.size());
    }

    @Test void historyContainsOnlyEarlierActiveEvaluationsOfTheSameStudent() {
        service.save(auth(3, "Coach"), 10, 20, 4, input(null));
        TrainingResult current = stored.getFirst();
        for (int i = 1; i <= 5; i++) {
            TrainingResult old = new TrainingResult(); old.setId(i + 1); old.setCoach(coach); old.setMember(member);
            old.setResult("Buổi " + i); old.setSession(ClassSession.builder().id(20 - i).classEntity(classroom)
                    .date(session.getDate().minusDays(i)).startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(9, 0)).build());
            stored.add(old);
        }
        var history = service.coachView(auth(3, "Coach"), 10, 20).students().getFirst().history();
        assertEquals(3, history.size()); assertEquals("Buổi 1", history.getFirst().result());
        stored.get(1).setWithdrawn(true); stored.get(2).setMember(other);
        history = service.coachView(auth(3, "Coach"), 10, 20).students().getFirst().history();
        assertEquals("Buổi 3", history.getFirst().result()); assertEquals(3, history.size());
        assertEquals(current.getId(), service.memberDetail(auth(4, "Member"), 20).id());
    }
}
