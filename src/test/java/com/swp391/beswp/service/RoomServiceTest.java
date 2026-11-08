package com.swp391.beswp.service;

import com.swp391.beswp.dto.RoomDtos.SaveRequest;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {
    @Mock RoomRepository rooms;
    @Mock ClassRepository classes;
    @Mock SubjectRepository subjects;
    RoomService service;
    Room room;
    Subject subject;

    @BeforeEach void setup() {
        service = new RoomService(rooms, classes, subjects, Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC));
        room = Room.builder().id(1).name("Yoga 1").type(RoomType.MULTIPURPOSE).maxCapacity(20).location("Floor 1").build();
        subject = Subject.builder().id(2).name("Yoga").allowedRoomTypes(new LinkedHashSet<>(Set.of(RoomType.MULTIPURPOSE))).build();
    }
    SaveRequest request(RoomType type, int capacity, String status) {
        return new SaveRequest("Yoga 1", type, capacity, "Floor 1", status);
    }
    ClassEntity assigned() {
        return ClassEntity.builder().id(9).name("Yoga morning").subject(subject).room(room).maxCapacity(15).build();
    }
    void lock() { when(rooms.findByIdForUpdate(1)).thenReturn(Optional.of(room)); }
    void assertConflict(Runnable action) {
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
    @Test void createNormalizesAndPersists() {
        when(rooms.save(any())).thenAnswer(i -> { Room r = i.getArgument(0); r.setId(1); return r; });
        var data = service.create(new SaveRequest(" Yoga 1 ", RoomType.MULTIPURPOSE, 20, " Floor 1 ", "Active"));
        assertEquals("Yoga 1", data.name()); assertEquals("Floor 1", data.location());
        assertEquals(RoomType.MULTIPURPOSE, data.type()); assertEquals("Active", data.status());
    }
    @Test void duplicateNameRejected() {
        when(rooms.existsByNameIgnoreCase("Yoga 1")).thenReturn(true);
        assertConflict(() -> service.create(request(RoomType.MULTIPURPOSE, 20, "Active")));
        verify(rooms, never()).save(any());
    }
    @Test void cannotDisableRoomWithUpcomingClassAndReportsClass() {
        lock(); when(classes.findUpcomingClassesByRoomId(eq(1), any())).thenReturn(List.of(assigned()));
        var ex = assertThrows(ResponseStatusException.class, () -> service.changeStatus(1, "Maintenance"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode()); assertTrue(ex.getReason().contains("#9 Yoga morning"));
        assertEquals("Active", room.getStatus());
    }
    @Test void roomWithoutUpcomingClassesCanBeDisabled() {
        lock(); assertEquals("Inactive", service.changeStatus(1, "Inactive").status());
    }
    @Test void capacityCannotDropBelowClassCapacity() {
        lock(); when(classes.findUpcomingClassesByRoomId(eq(1), any())).thenReturn(List.of(assigned()));
        assertConflict(() -> service.update(1, request(RoomType.MULTIPURPOSE, 14, "Active")));
        assertEquals(20, room.getMaxCapacity());
    }
    @Test void incompatibleTypeChangeRejected() {
        lock(); when(classes.findUpcomingClassesByRoomId(eq(1), any())).thenReturn(List.of(assigned()));
        assertConflict(() -> service.update(1, request(RoomType.GYM, 20, "Active")));
        assertEquals(RoomType.MULTIPURPOSE, room.getType());
    }
    @Test void compatibleTypeChangeAllowed() {
        lock(); subject.getAllowedRoomTypes().add(RoomType.GYM);
        when(classes.findUpcomingClassesByRoomId(eq(1), any())).thenReturn(List.of(assigned()));
        assertEquals(RoomType.GYM, service.update(1, request(RoomType.GYM, 20, "Active")).type());
    }
    @Test void deleteHistoricalRoomRejected() {
        lock(); when(classes.existsByRoom_Id(1)).thenReturn(true);
        assertConflict(() -> service.delete(1)); verify(rooms, never()).delete(any());
    }
    @Test void deleteUnusedRoomAllowed() { lock(); service.delete(1); verify(rooms).delete(room); }
    @Test void concurrentNewSubjectRequiresRetry() {
        lock(); when(classes.findSubjectIdsByRoomId(1)).thenReturn(List.of(), List.of(2));
        assertConflict(() -> service.changeStatus(1, "Inactive"));
    }
    @Test void legacyUnclassifiedAndMaintenanceRoomsAreNotUsable() {
        assertTrue(RoomService.usable(room)); room.setType(null); assertFalse(RoomService.usable(room));
        room.setType(RoomType.MULTIPURPOSE); room.setStatus("Maintenance"); assertFalse(RoomService.usable(room));
        assertEquals(12, service.options().types().size());
        assertTrue(service.options().types().stream().anyMatch(t -> t.code() == RoomType.MARTIAL_ARTS && t.label().equals("Phòng Võ")));
        assertTrue(service.options().types().stream().anyMatch(t -> t.code() == RoomType.MULTIPURPOSE && t.label().equals("Phòng Đa Năng")));
    }
}
