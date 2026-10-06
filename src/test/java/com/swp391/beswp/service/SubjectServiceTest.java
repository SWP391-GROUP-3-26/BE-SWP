package com.swp391.beswp.service;

import com.swp391.beswp.dto.CreateSubjectRequest;
import com.swp391.beswp.dto.SubjectListResponse;
import com.swp391.beswp.dto.SubjectResponse;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private SubjectService subjectService;

    private CreateSubjectRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = CreateSubjectRequest.builder()
                .name("Yoga Phục Hồi Cơ Khớp")
                .category("Yoga")
                .description("Giảm áp lực cột sống, tăng độ dẻo dai với dụng cụ hỗ trợ")
                .build();
    }

    @Test
    void createSubject_success() {
        when(subjectRepository.existsByNameIgnoreCase("Yoga Phục Hồi Cơ Khớp")).thenReturn(false);

        Subject savedSubject = Subject.builder()
                .id(1)
                .name("Yoga Phục Hồi Cơ Khớp")
                .category("Yoga")
                .description("Giảm áp lực cột sống, tăng độ dẻo dai với dụng cụ hỗ trợ")
                .build();
        when(subjectRepository.save(any(Subject.class))).thenReturn(savedSubject);

        SubjectResponse response = subjectService.createSubject(validRequest);

        assertTrue(response.isSuccess());
        assertEquals("Tạo môn học thành công", response.getMessage());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getId());
        assertEquals("SUB-001", response.getData().getCode());
        assertEquals("Yoga Phục Hồi Cơ Khớp", response.getData().getName());
        assertEquals("Yoga", response.getData().getCategory());
        assertEquals("Giảm áp lực cột sống, tăng độ dẻo dai với dụng cụ hỗ trợ", response.getData().getDescription());

        verify(subjectRepository).save(any(Subject.class));
    }

    @Test
    void createSubject_duplicateName_throwsConflict() {
        when(subjectRepository.existsByNameIgnoreCase("Yoga Phục Hồi Cơ Khớp")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                subjectService.createSubject(validRequest)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("đã tồn tại trong hệ thống"));
        verify(subjectRepository, never()).save(any(Subject.class));
    }

    @Test
    void createSubject_blankName_throwsBadRequest() {
        CreateSubjectRequest blankNameRequest = CreateSubjectRequest.builder()
                .name("   ")
                .category("Yoga")
                .description("Mô tả")
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                subjectService.createSubject(blankNameRequest)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void getAllSubjects_returnsListWithFormattedCode() {
        Subject s1 = Subject.builder().id(1).name("Yoga Phục Hồi").category("Yoga").description("Mô tả 1").build();
        Subject s2 = Subject.builder().id(2).name("Pilates Reformer").category("Pilates").description("Mô tả 2").build();

        when(subjectRepository.searchSubjects(null, null, null)).thenReturn(List.of(s1, s2));

        SubjectListResponse response = subjectService.getAllSubjects(null, null);

        assertTrue(response.isSuccess());
        assertEquals(2, response.getTotal());
        assertEquals("SUB-001", response.getData().get(0).getCode());
        assertEquals("SUB-002", response.getData().get(1).getCode());
    }

    @Test
    void deleteSubject_success() {
        Subject s = Subject.builder().id(1).name("Yoga").build();
        when(subjectRepository.findById(1)).thenReturn(Optional.of(s));

        subjectService.deleteSubject(1);

        verify(subjectRepository).delete(s);
    }

    @Test
    void deleteSubject_notFound_throwsNotFound() {
        when(subjectRepository.findById(999)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                subjectService.deleteSubject(999)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(subjectRepository, never()).delete(any());
    }
}
