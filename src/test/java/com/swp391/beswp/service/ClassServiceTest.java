package com.swp391.beswp.service;

import com.swp391.beswp.dto.ClassFormOptionsResponse;
import com.swp391.beswp.dto.ClassListResponse;
import com.swp391.beswp.dto.ClassResponse;
import com.swp391.beswp.dto.CreateClassRequest;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.Role;
import com.swp391.beswp.entity.Room;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.RoomRepository;
import com.swp391.beswp.repository.SubjectRepository;
import com.swp391.beswp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private ClassService classService;

    private Subject sampleSubject;
    private User sampleCoach;
    private Room sampleRoom;
    private CreateClassRequest validRequest;

    @BeforeEach
    void setUp() {
        sampleSubject = Subject.builder()
                .id(1)
                .name("Yoga Phục Hồi")
                .category("Yoga")
                .build();

        Role coachRole = new Role();
        coachRole.setId(3);
        coachRole.setRoleName("Coach");

        sampleCoach = new User();
        sampleCoach.setId(3);
        sampleCoach.setFullName("HLV Đặng Thu Trang");
        sampleCoach.setUsername("coach_trang");
        sampleCoach.setRole(coachRole);
        sampleCoach.setStatus("Active");

        sampleRoom = Room.builder()
                .id(1)
                .name("Studio Lotus 01")
                .maxCapacity(20)
                .build();

        validRequest = CreateClassRequest.builder()
                .name("Yoga Chữa Lành Buổi Sáng")
                .subjectId(1)
                .coachId(3)
                .roomId(1)
                .daysOfWeek(List.of("T2", "T4", "T6"))
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(8, 30))
                .maxCapacity(15)
                .price(new BigDecimal("1500000"))
                .status("Active")
                .build();
    }

    @Test
    void createClass_success() {
        when(classRepository.existsByNameIgnoreCase("Yoga Chữa Lành Buổi Sáng")).thenReturn(false);
        when(subjectRepository.findById(1)).thenReturn(Optional.of(sampleSubject));
        when(userRepository.findById(3)).thenReturn(Optional.of(sampleCoach));
        when(roomRepository.findById(1)).thenReturn(Optional.of(sampleRoom));
        when(classRepository.findActiveClassesByRoomId(1)).thenReturn(Collections.emptyList());
        when(classRepository.findActiveClassesByCoachId(3)).thenReturn(Collections.emptyList());

        ClassEntity saved = ClassEntity.builder()
                .id(1)
                .name(validRequest.getName())
                .subject(sampleSubject)
                .coach(sampleCoach)
                .room(sampleRoom)
                .daysOfWeek("T2, T4, T6")
                .startTime(validRequest.getStartTime())
                .endTime(validRequest.getEndTime())
                .maxCapacity(15)
                .price(validRequest.getPrice())
                .status("Active")
                .build();
        when(classRepository.save(any(ClassEntity.class))).thenReturn(saved);

        ClassResponse response = classService.createClass(validRequest);

        assertTrue(response.isSuccess());
        assertEquals("Tạo mới lớp học thành công", response.getMessage());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getId());
        assertEquals("CLS-001", response.getData().getCode());
        assertEquals("Yoga Chữa Lành Buổi Sáng", response.getData().getName());
        assertEquals("Studio Lotus 01", response.getData().getRoom().getName());
        assertEquals("HLV Đặng Thu Trang", response.getData().getCoach().getFullName());
        assertEquals("07:00 - 08:30", response.getData().getTimeSlot());
        assertEquals(new BigDecimal("1500000"), response.getData().getPrice());

        verify(classRepository).save(any(ClassEntity.class));
    }

    @Test
    void createClass_duplicateName_throwsConflict() {
        when(classRepository.existsByNameIgnoreCase("Yoga Chữa Lành Buổi Sáng")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                classService.createClass(validRequest)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("đã tồn tại trong hệ thống"));
    }

    @Test
    void createClass_capacityExceedsRoom_throwsBadRequest() {
        validRequest.setMaxCapacity(25); // Room capacity is 20
        when(classRepository.existsByNameIgnoreCase(validRequest.getName())).thenReturn(false);
        when(subjectRepository.findById(1)).thenReturn(Optional.of(sampleSubject));
        when(userRepository.findById(3)).thenReturn(Optional.of(sampleCoach));
        when(roomRepository.findById(1)).thenReturn(Optional.of(sampleRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                classService.createClass(validRequest)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("vượt quá sức chứa tối đa của phòng"));
    }

    @Test
    void createClass_endTimeBeforeStartTime_throwsBadRequest() {
        validRequest.setStartTime(LocalTime.of(9, 0));
        validRequest.setEndTime(LocalTime.of(8, 0));
        when(classRepository.existsByNameIgnoreCase(validRequest.getName())).thenReturn(false);
        when(subjectRepository.findById(1)).thenReturn(Optional.of(sampleSubject));
        when(userRepository.findById(3)).thenReturn(Optional.of(sampleCoach));
        when(roomRepository.findById(1)).thenReturn(Optional.of(sampleRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                classService.createClass(validRequest)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Giờ kết thúc"));
    }

    @Test
    void createClass_roomScheduleConflict_throwsConflict() {
        when(classRepository.existsByNameIgnoreCase(validRequest.getName())).thenReturn(false);
        when(subjectRepository.findById(1)).thenReturn(Optional.of(sampleSubject));
        when(userRepository.findById(3)).thenReturn(Optional.of(sampleCoach));
        when(roomRepository.findById(1)).thenReturn(Optional.of(sampleRoom));

        ClassEntity existing = ClassEntity.builder()
                .id(99)
                .name("Yoga Khác")
                .room(sampleRoom)
                .daysOfWeek("T2, T5")
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(9, 0))
                .status("Active")
                .build();
        when(classRepository.findActiveClassesByRoomId(1)).thenReturn(List.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                classService.createClass(validRequest)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Xung đột phòng tập"));
    }

    @Test
    void getFormOptions_returnsAvailableOptions() {
        when(subjectRepository.findAll()).thenReturn(List.of(sampleSubject));
        when(userRepository.findByRole_RoleNameIgnoreCaseAndStatusIgnoreCase("Coach", "Active"))
                .thenReturn(List.of(sampleCoach));
        when(roomRepository.findAllByOrderByNameAsc()).thenReturn(List.of(sampleRoom));

        ClassFormOptionsResponse options = classService.getFormOptions();

        assertTrue(options.isSuccess());
        assertEquals(1, options.getData().getSubjects().size());
        assertEquals(1, options.getData().getCoaches().size());
        assertEquals(1, options.getData().getRooms().size());
        assertEquals("Yoga Phục Hồi (SUB-001)", options.getData().getSubjects().get(0).getDisplayText());
    }

    @Test
    void deleteClass_success() {
        ClassEntity c = ClassEntity.builder().id(1).name("Test Class").build();
        when(classRepository.findById(1)).thenReturn(Optional.of(c));

        classService.deleteClass(1);

        verify(classRepository).delete(c);
    }
}
