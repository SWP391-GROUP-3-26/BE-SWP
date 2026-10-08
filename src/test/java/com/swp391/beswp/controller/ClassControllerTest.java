package com.swp391.beswp.controller;

import com.swp391.beswp.dto.ClassFormOptionsResponse;
import com.swp391.beswp.dto.ClassListResponse;
import com.swp391.beswp.dto.ClassResponse;
import com.swp391.beswp.dto.CreateClassRequest;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.service.ClassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassControllerTest {

    @Mock
    private ClassService classService;

    @InjectMocks
    private ClassController classController;

    private CreateClassRequest validRequest;

    @BeforeEach
    void setUp() {
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
                .build();
    }

    @Test
    void createClass_returns201Created() {
        ClassEntity classEntity = ClassEntity.builder()
                .id(1)
                .name("Yoga Chữa Lành Buổi Sáng")
                .maxCapacity(15)
                .price(new BigDecimal("1500000"))
                .build();

        ClassResponse expected = ClassResponse.success("Tạo mới lớp học thành công", classEntity);
        when(classService.createClass(validRequest)).thenReturn(expected);

        ResponseEntity<ClassResponse> response = classController.createClass(validRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("CLS-001", response.getBody().getData().getCode());
        verify(classService).createClass(validRequest);
    }

    @Test
    void getAllClasses_returns200Ok() {
        ClassListResponse expected = ClassListResponse.builder()
                .success(true)
                .total(1)
                .data(List.of())
                .build();
        when(classService.getAllClasses("Yoga", "Active")).thenReturn(expected);

        ResponseEntity<ClassListResponse> response = classController.getAllClasses("Yoga", "Active");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().isSuccess());
    }

    @Test
    void getFormOptions_returns200Ok() {
        ClassFormOptionsResponse expected = ClassFormOptionsResponse.builder()
                .success(true)
                .message("OK")
                .build();
        when(classService.getFormOptions()).thenReturn(expected);

        ResponseEntity<ClassFormOptionsResponse> response = classController.getFormOptions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void deleteClass_returns200Ok() {
        doNothing().when(classService).deleteClass(1);

        ResponseEntity<Map<String, Object>> response = classController.deleteClass(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("success"));
    }
}
