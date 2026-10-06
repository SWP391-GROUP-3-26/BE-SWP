package com.swp391.beswp.service;

import com.swp391.beswp.dto.CreateSubjectRequest;
import com.swp391.beswp.dto.SubjectListResponse;
import com.swp391.beswp.dto.SubjectResponse;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    @Transactional
    public SubjectResponse createSubject(CreateSubjectRequest request) {
        String name = normalizeText(request.getName());
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên môn học không được để trống");
        }

        if (subjectRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên môn học '" + name + "' đã tồn tại trong hệ thống");
        }

        String category = normalizeText(request.getCategory());
        String description = normalizeText(request.getDescription());

        Subject subject = Subject.builder()
                .name(name)
                .category(StringUtils.hasText(category) ? category : null)
                .description(StringUtils.hasText(description) ? description : null)
                .build();

        Subject savedSubject = subjectRepository.save(subject);
        return SubjectResponse.success("Tạo môn học thành công", savedSubject);
    }

    @Transactional(readOnly = true)
    public SubjectListResponse getAllSubjects(String search, String category) {
        String keyword = normalizeText(search);
        String cat = normalizeText(category);

        Integer parsedId = parseSubjectId(keyword);

        List<Subject> subjects = subjectRepository.searchSubjects(
                StringUtils.hasText(keyword) ? keyword : null,
                parsedId,
                StringUtils.hasText(cat) ? cat : null
        );

        return SubjectListResponse.success(subjects);
    }

    @Transactional(readOnly = true)
    public SubjectResponse getSubjectById(Integer id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy môn học với mã ID: " + id
                ));
        return SubjectResponse.success("Lấy thông tin môn học thành công", subject);
    }

    @Transactional
    public void deleteSubject(Integer id) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy môn học với mã ID: " + id
                ));
        subjectRepository.delete(subject);
    }

    private String normalizeText(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseSubjectId(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String cleaned = keyword.trim().toUpperCase();
        if (cleaned.startsWith("SUB-")) {
            String numPart = cleaned.substring(4);
            try {
                return Integer.parseInt(numPart);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
