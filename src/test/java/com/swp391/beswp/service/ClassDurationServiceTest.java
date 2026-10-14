package com.swp391.beswp.service;

import com.swp391.beswp.dto.CreateClassRequest;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ClassDurationServiceTest {
    private final ClassRepository classes = mock(ClassRepository.class);
    private final ClassSessionRepository sessions = mock(ClassSessionRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-13T01:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ClassService service = new ClassService(classes, subjects, users, rooms, sessions, clock);
    private final List<ClassSession> created = new ArrayList<>();
    private Room room;
    private User coach;
    private CreateClassRequest request;

    @BeforeEach
    void setUp() {
        Role role = new Role(); role.setRoleName("Coach");
        coach = new User(); coach.setId(3); coach.setRole(role); coach.setFullName("Coach");
        room = Room.builder().id(1).name("Room").maxCapacity(20).build();
        when(subjects.findById(1)).thenReturn(Optional.of(Subject.builder().id(1).name("Yoga").build()));
        when(users.findByIdForUpdate(3)).thenReturn(Optional.of(coach));
        when(rooms.findByIdForUpdate(1)).thenReturn(Optional.of(room));
        when(classes.save(any())).thenAnswer(i -> { ClassEntity c = i.getArgument(0); c.setId(10); return c; });
        when(sessions.saveAll(any())).thenAnswer(i -> { Iterable<ClassSession> rows = i.getArgument(0); rows.forEach(created::add); return created; });
        request = CreateClassRequest.builder().name("Yoga").subjectId(1).coachId(3).roomId(1)
                .daysOfWeek(List.of("Thứ 5", "Thứ 3")).slotId(1).startTime(LocalTime.of(7, 30)).endTime(LocalTime.of(9, 0))
                .startDate(LocalDate.of(2026, 10, 13)).durationWeeks(1).price(BigDecimal.valueOf(100000)).maxCapacity(10).build();
    }

    @Test
    void createPersistsExactlyTheFiniteTimetableAndReturnsComputedTerm() {
        var response = service.createClass(request).getData();
        assertEquals(LocalDate.of(2026, 10, 18), response.getEndDate());
        assertEquals(1, response.getDurationWeeks());
        assertEquals(List.of("T3", "T5"), response.getDaysOfWeek());
        assertEquals(List.of(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15)), created.stream().map(ClassSession::getDate).toList());
        assertTrue(created.stream().allMatch(s -> s.getClassEntity().getId() == 10 && s.getStartTime().equals(request.getStartTime())
                && s.getEndTime().equals(request.getEndTime()) && "Scheduled".equals(s.getStatus())));
    }

    @Test
    void missingDurationOrStartWrongFirstDayAndInvalidDayWriteNothing() {
        request.setDurationWeeks(null); assertThrows(ResponseStatusException.class, () -> service.createClass(request));
        request.setDurationWeeks(1); request.setStartDate(null); assertThrows(ResponseStatusException.class, () -> service.createClass(request));
        request.setStartDate(LocalDate.of(2026, 10, 14)); assertThrows(ResponseStatusException.class, () -> service.createClass(request));
        request.setStartDate(LocalDate.of(2026, 10, 13)); request.setDaysOfWeek(List.of("Thứ 8"));
        assertThrows(ResponseStatusException.class, () -> service.createClass(request));
        verify(classes, never()).save(any()); verify(sessions, never()).saveAll(any());
    }

    private ClassEntity existing(String status, LocalDate date) {
        ClassEntity c = ClassEntity.builder().id(99).name("Existing").coach(coach).room(room).status(status).build();
        when(sessions.findActiveSessionsByClassId(99)).thenReturn(List.of(ClassSession.builder().classEntity(c)
                .date(date).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 15)).status("Scheduled").build()));
        return c;
    }

    @Test
    void sameActualDateAndOverlappingTimeBlocksRoomAndCoach() {
        ClassEntity c = existing("Active", LocalDate.of(2026, 10, 15));
        when(classes.findActiveClassesByRoomId(1)).thenReturn(List.of(c));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.createClass(request)).getStatusCode().value());
        when(classes.findActiveClassesByRoomId(1)).thenReturn(List.of());
        when(classes.findActiveClassesByCoachId(3)).thenReturn(List.of(c));
        var error = assertThrows(ResponseStatusException.class, () -> service.createClass(request));
        assertTrue(error.getReason().contains("HLV"));
    }

    @Test
    void disjointDatesOrTouchingTimesOrCompletedClassesDoNotConflict() {
        ClassEntity c = existing("Active", LocalDate.of(2026, 10, 22));
        when(classes.findActiveClassesByRoomId(1)).thenReturn(List.of(c));
        assertTrue(service.createClass(request).isSuccess());
        c = existing("Completed", LocalDate.of(2026, 10, 15));
        when(classes.findActiveClassesByRoomId(1)).thenReturn(List.of(c));
        assertTrue(service.createClass(request).isSuccess());
        c = existing("Active", LocalDate.of(2026, 10, 15));
        when(sessions.findActiveSessionsByClassId(99)).thenReturn(List.of(ClassSession.builder().classEntity(c)
                .date(LocalDate.of(2026, 10, 15)).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(9, 15)).build()));
        when(classes.findActiveClassesByRoomId(1)).thenReturn(List.of(c));
        assertTrue(service.createClass(request).isSuccess());
    }

    @Test
    void classListAndDetailExposeExpiryWithoutWaitingForScheduler() {
        ClassEntity c = ClassEntity.builder().id(1).status("Open").date(LocalDate.of(2026, 10, 5))
                .endDate(LocalDate.of(2026, 10, 11)).durationWeeks(1).daysOfWeek("Thứ 2").build();
        when(classes.searchClasses(null, null, null)).thenReturn(List.of(c));
        when(classes.findById(1)).thenReturn(Optional.of(c));
        assertEquals(0, service.getAllClasses(null, "Active").getTotal());
        assertEquals("Completed", service.getAllClasses(null, "Completed").getData().getFirst().getStatus());
        assertEquals("Completed", service.getClassById(1).getData().getStatus());
        assertEquals("Open", c.getStatus());
    }

    @Test
    void backdatedCreationAlreadyBeyondEndDatePersistsCompletedClassAndSessions() {
        request.setStartDate(LocalDate.of(2026, 10, 6));
        ClassEntity occupied = existing("Active", LocalDate.of(2026, 10, 8));
        when(classes.findActiveClassesByRoomId(1)).thenReturn(List.of(occupied));
        var response = service.createClass(request).getData();
        assertEquals("Completed", response.getStatus());
        assertEquals(LocalDate.of(2026, 10, 11), response.getEndDate());
        assertTrue(created.stream().allMatch(s -> "Completed".equals(s.getStatus())));
        verify(classes).save(argThat(c -> "Completed".equals(c.getStatus())));
        verify(classes, never()).findActiveClassesByRoomId(any());
    }
}
