package com.swp391.beswp.service;

import com.swp391.beswp.dto.CreateSubjectRequest;
import com.swp391.beswp.dto.SubjectListResponse;
import com.swp391.beswp.dto.SubjectResponse;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.entity.RoomType;
import com.swp391.beswp.repository.SubjectRepository;
import com.swp391.beswp.repository.RoomRepository;
import com.swp391.beswp.repository.ClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final RoomRepository roomRepository;
    private final ClassRepository classRepository;
    private final Clock paymentClock;

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
        var roomTypes = validatedTypes(request.getAllowedRoomTypes());

        Subject subject = Subject.builder()
                .name(name)
                .category(StringUtils.hasText(category) ? category : null)
                .description(StringUtils.hasText(description) ? description : null)
                .allowedRoomTypes(roomTypes)
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

    @Transactional
    public SubjectResponse updateRoomTypes(Integer id, List<RoomType> types) {
        var allowed = validatedTypes(types);
        Subject subject = subjectRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy môn học"));
        // Match class creation's subject -> room order. Room edits also hold these room locks.
        for (Integer roomId : classRepository.findRoomIdsBySubjectId(id)) {
            var room = roomRepository.findByIdForUpdate(roomId).orElseThrow();
            var assigned = classRepository.findUpcomingClassesByRoomId(roomId, LocalDateTime.now(paymentClock));
            if (room.getType() != null && !allowed.contains(room.getType())
                    && assigned.stream().anyMatch(c -> c.getSubject().getId().equals(id))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Loại phòng đang được lớp của môn sử dụng: " + room.getName() + "; cần xử lý lớp trước");
            }
        }
        subject.getAllowedRoomTypes().clear(); subject.getAllowedRoomTypes().addAll(allowed);
        return SubjectResponse.success("Cập nhật loại phòng phù hợp thành công", subject);
    }

    private Set<RoomType> validatedTypes(List<RoomType> types) {
        if (types == null || types.isEmpty() || types.stream().anyMatch(Objects::isNull)
                || new HashSet<>(types).size() != types.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phải chọn ít nhất một loại phòng hợp lệ, không trùng lặp");
        }
        return new LinkedHashSet<>(types);
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
