package com.swp391.beswp.service;

import com.swp391.beswp.dto.CoachAttendanceDtos.AttendanceEntry;
import com.swp391.beswp.dto.CoachAttendanceDtos.SaveAttendanceRequest;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class CoachAttendanceServiceTest {
    private final ClassRepository classes = mock(ClassRepository.class);
    private final ClassSessionRepository sessions = mock(ClassSessionRepository.class);
    private final CoachAttendanceRepository attendance = mock(CoachAttendanceRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TrainingResultRepository results = mock(TrainingResultRepository.class);
    private final CoachAttendanceService service = new CoachAttendanceService(classes, sessions, attendance, users, results);
    private final Authentication auth = new UsernamePasswordAuthenticationToken(
            "3", null, List.of(new SimpleGrantedAuthority("ROLE_Coach")));

    @BeforeEach
    void setup() {
        Role role = new Role(); role.setRoleName("Coach");
        User coach = new User(); coach.setId(3); coach.setRole(role); coach.setStatus("Active");
        User member = new User(); member.setId(4);
        ClassEntity classroom = ClassEntity.builder().id(10).coach(coach).status("Active").build();
        ClassSession session = ClassSession.builder().id(20).classEntity(classroom).status("Scheduled").build();
        when(users.findById(3)).thenReturn(Optional.of(coach));
        when(classes.findByIdForUpdate(10)).thenReturn(Optional.of(classroom));
        when(sessions.findById(20)).thenReturn(Optional.of(session));
        when(attendance.findRegisteredMembersForSession(20)).thenReturn(List.of(member));
        when(attendance.findBySession_Id(20)).thenReturn(List.of());
        when(attendance.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({"Present,Present", "Absent,Absent", "present,Present", "ABSENT,Absent"})
    void savesOnlyPresentOrAbsentAndReturnsCorrectCounts(String input, String expected) {
        var response = service.saveSessionAttendance(auth, 10, 20,
                new SaveAttendanceRequest(List.of(new AttendanceEntry(4, input, " Ghi chú "))));
        var data = response.data();
        assertEquals(expected, data.members().getFirst().status());
        assertEquals("Ghi chú", data.members().getFirst().note());
        assertEquals(1, data.session().markedMembers());
        assertEquals(expected.equals("Present") ? 1 : 0, data.session().presentCount());
        assertEquals(expected.equals("Absent") ? 1 : 0, data.session().absentCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Late", "Excused", "late", "EXCUSED", "Unknown", ""})
    void rejectsRemovedStatusesWithoutSaving(String status) {
        var error = assertThrows(ResponseStatusException.class, () -> service.saveSessionAttendance(auth, 10, 20,
                new SaveAttendanceRequest(List.of(new AttendanceEntry(4, status, null)))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(attendance, never()).saveAll(anyList());
    }

    @Test
    void requestValidationAcceptsTwoStatusesAndRejectsRemovedChoices() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String status : List.of("Present", "Absent", "present", "ABSENT")) {
                assertTrue(validator.validate(new SaveAttendanceRequest(
                        List.of(new AttendanceEntry(4, status, null)))).isEmpty(), status);
            }
            for (String status : List.of("Late", "Excused")) {
                assertFalse(validator.validate(new SaveAttendanceRequest(
                        List.of(new AttendanceEntry(4, status, null)))).isEmpty(), status);
            }
        }
    }

    @Test
    void requiresWithdrawalBeforeChangingEvaluatedStudentToAbsent() {
        when(results.existsBySession_IdAndMember_IdAndWithdrawnFalse(20, 4)).thenReturn(true);
        var error = assertThrows(ResponseStatusException.class, () -> service.saveSessionAttendance(auth, 10, 20,
                new SaveAttendanceRequest(List.of(new AttendanceEntry(4, "Absent", null)))));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(attendance, never()).saveAll(anyList());
        when(results.existsBySession_IdAndMember_IdAndWithdrawnFalse(20, 4)).thenReturn(false);
        assertEquals("Absent", service.saveSessionAttendance(auth, 10, 20,
                new SaveAttendanceRequest(List.of(new AttendanceEntry(4, "Absent", null)))).data().members().getFirst().status());
    }
}
