package com.swp391.beswp.controller;

import com.swp391.beswp.dto.CreateSubjectRequest;
import com.swp391.beswp.dto.SubjectListResponse;
import com.swp391.beswp.dto.SubjectResponse;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.service.SubjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubjectControllerTest {

    @Mock
    private SubjectService subjectService;

    @InjectMocks
    private SubjectController subjectController;

    private CreateSubjectRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = CreateSubjectRequest.builder()
                .name("Yoga Phục Hồi Cơ Khớp")
                .category("Yoga")
                .description("Giảm áp lực cột sống, tăng độ dẻo dai")
                .build();
    }

    @Test
    void createSubject_returns201Created() {
        Subject subject = Subject.builder()
                .id(1)
                .name("Yoga Phục Hồi Cơ Khớp")
                .category("Yoga")
                .description("Giảm áp lực cột sống, tăng độ dẻo dai")
                .build();

        SubjectResponse expectedResponse = SubjectResponse.success("Tạo môn học thành công", subject);
        when(subjectService.createSubject(validRequest)).thenReturn(expectedResponse);

        ResponseEntity<SubjectResponse> responseEntity = subjectController.createSubject(validRequest);

        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals("Tạo môn học thành công", responseEntity.getBody().getMessage());
        assertEquals("SUB-001", responseEntity.getBody().getData().getCode());
        assertEquals("Yoga Phục Hồi Cơ Khớp", responseEntity.getBody().getData().getName());
        assertEquals("Yoga", responseEntity.getBody().getData().getCategory());

        verify(subjectService).createSubject(validRequest);
    }

    @Test
    void getAllSubjects_returns200Ok() {
        Subject s1 = Subject.builder().id(1).name("Yoga Phục Hồi").category("Yoga").description("Mô tả").build();
        SubjectListResponse expectedList = SubjectListResponse.success(List.of(s1));
        when(subjectService.getAllSubjects("Yoga", "Yoga")).thenReturn(expectedList);

        ResponseEntity<SubjectListResponse> responseEntity = subjectController.getAllSubjects("Yoga", "Yoga");

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(1, responseEntity.getBody().getTotal());
        assertEquals("SUB-001", responseEntity.getBody().getData().get(0).getCode());
    }

    @Test
    void getSubjectById_returns200Ok() {
        Subject s1 = Subject.builder().id(1).name("Yoga Phục Hồi").category("Yoga").description("Mô tả").build();
        SubjectResponse expected = SubjectResponse.success("Lấy thông tin môn học thành công", s1);
        when(subjectService.getSubjectById(1)).thenReturn(expected);

        ResponseEntity<SubjectResponse> responseEntity = subjectController.getSubjectById(1);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals("SUB-001", responseEntity.getBody().getData().getCode());
    }

    @Test
    void deleteSubject_returns200Ok() {
        doNothing().when(subjectService).deleteSubject(1);

        ResponseEntity<Map<String, Object>> responseEntity = subjectController.deleteSubject(1);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(true, responseEntity.getBody().get("success"));
        assertEquals("Xóa môn học thành công", responseEntity.getBody().get("message"));

        verify(subjectService).deleteSubject(1);
    }
}
