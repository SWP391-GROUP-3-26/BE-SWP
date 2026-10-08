package com.swp391.beswp.service;

import com.swp391.beswp.dto.ClassFormOptionsResponse;
import com.swp391.beswp.dto.ClassListResponse;
import com.swp391.beswp.dto.ClassResponse;
import com.swp391.beswp.dto.CreateClassRequest;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.Room;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.RoomRepository;
import com.swp391.beswp.repository.SubjectRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final ClassRepository classRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    @Transactional
    public ClassResponse createClass(CreateClassRequest request) {
        String name = normalizeText(request.getName());
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên lớp học không được để trống");
        }

        if (classRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên lớp học '" + name + "' đã tồn tại trong hệ thống");
        }

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Môn học với ID " + request.getSubjectId() + " không tồn tại"
                ));

        User coach = userRepository.findById(request.getCoachId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Huấn luyện viên với ID " + request.getCoachId() + " không tồn tại"
                ));

        if (coach.getRole() == null || !"Coach".equalsIgnoreCase(coach.getRole().getRoleName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Người dùng '" + coach.getFullName() + "' không có vai trò là Huấn Luyện Viên (Coach)"
            );
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Phòng tập với ID " + request.getRoomId() + " không tồn tại"
                ));

        if (request.getMaxCapacity() > room.getMaxCapacity()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Sức chứa tối đa của lớp (%d học viên) vượt quá sức chứa tối đa của phòng tập '%s' (%d người)",
                            request.getMaxCapacity(), room.getName(), room.getMaxCapacity())
            );
        }

        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();
        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khung giờ học không được để trống");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Giờ kết thúc (" + endTime + ") phải sau giờ bắt đầu (" + startTime + ")"
            );
        }

        List<String> rawDays = request.getDaysOfWeek() != null ? request.getDaysOfWeek() : Collections.emptyList();
        List<String> cleanedDays = rawDays.stream()
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (cleanedDays.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ít nhất một ngày học trong tuần");
        }

        validateScheduleConflicts(room.getId(), coach.getId(), startTime, endTime, cleanedDays, null);

        String daysOfWeekStr = String.join(", ", cleanedDays);
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : "Active";

        ClassEntity newClass = ClassEntity.builder()
                .name(name)
                .subject(subject)
                .coach(coach)
                .room(room)
                .maxCapacity(request.getMaxCapacity())
                .price(request.getPrice())
                .daysOfWeek(daysOfWeekStr)
                .startTime(startTime)
                .endTime(endTime)
                .status(status)
                .date(request.getStartDate())
                .build();

        ClassEntity saved = classRepository.save(newClass);
        return ClassResponse.success("Tạo mới lớp học thành công", saved);
    }

    @Transactional(readOnly = true)
    public ClassListResponse getAllClasses(String search, String status) {
        String keyword = normalizeText(search);
        String st = normalizeText(status);

        if ("all".equalsIgnoreCase(st) || "tất cả".equalsIgnoreCase(st)) {
            st = null;
        }

        Integer parsedId = parseClassId(keyword);

        List<ClassEntity> classes = classRepository.searchClasses(
                StringUtils.hasText(keyword) ? keyword : null,
                parsedId,
                StringUtils.hasText(st) ? st : null
        );

        return ClassListResponse.success(classes);
    }

    @Transactional(readOnly = true)
    public ClassResponse getClassById(Integer id) {
        ClassEntity classEntity = classRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lớp học với mã ID: " + id
                ));
        return ClassResponse.success("Lấy thông tin lớp học thành công", classEntity);
    }

    @Transactional(readOnly = true)
    public ClassFormOptionsResponse getFormOptions() {
        List<Subject> subjects = subjectRepository.findAll();
        List<User> coaches = userRepository.findByRole_RoleNameIgnoreCaseAndStatusIgnoreCase("Coach", "Active");
        if (coaches.isEmpty()) {
            coaches = userRepository.findByRole_RoleNameIgnoreCase("Coach");
        }
        List<Room> rooms = roomRepository.findAllByOrderByNameAsc();

        List<ClassFormOptionsResponse.SubjectOption> subjectOptions = subjects.stream()
                .map(s -> ClassFormOptionsResponse.SubjectOption.builder()
                        .id(s.getId())
                        .code(String.format("SUB-%03d", s.getId()))
                        .name(s.getName())
                        .category(s.getCategory())
                        .displayText(String.format("%s (%s)", s.getName(), String.format("SUB-%03d", s.getId())))
                        .build())
                .toList();

        List<ClassFormOptionsResponse.CoachOption> coachOptions = coaches.stream()
                .map(c -> ClassFormOptionsResponse.CoachOption.builder()
                        .id(c.getId())
                        .fullName(c.getFullName())
                        .username(c.getUsername())
                        .phone(c.getPhone())
                        .displayText("HLV " + c.getFullName())
                        .build())
                .toList();

        List<ClassFormOptionsResponse.RoomOption> roomOptions = rooms.stream()
                .map(r -> ClassFormOptionsResponse.RoomOption.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .maxCapacity(r.getMaxCapacity())
                        .displayText(String.format("%s (Tối đa %d người)", r.getName(), r.getMaxCapacity()))
                        .build())
                .toList();

        return ClassFormOptionsResponse.builder()
                .success(true)
                .message("Lấy dữ liệu danh mục tạo lớp học thành công")
                .data(ClassFormOptionsResponse.OptionData.builder()
                        .subjects(subjectOptions)
                        .coaches(coachOptions)
                        .rooms(roomOptions)
                        .build())
                .build();
    }

    @Transactional
    public void deleteClass(Integer id) {
        ClassEntity classEntity = classRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lớp học với mã ID: " + id
                ));
        classRepository.delete(classEntity);
    }

    private void validateScheduleConflicts(
            Integer roomId,
            Integer coachId,
            LocalTime newStart,
            LocalTime newEnd,
            List<String> newDays,
            Integer excludeClassId
    ) {
        List<ClassEntity> roomClasses = classRepository.findActiveClassesByRoomId(roomId);
        for (ClassEntity existing : roomClasses) {
            if (excludeClassId != null && existing.getId().equals(excludeClassId)) {
                continue;
            }
            if (isTimeOverlap(newStart, newEnd, existing.getStartTime(), existing.getEndTime())) {
                String overlapDay = findCommonDay(newDays, existing.getDaysOfWeek());
                if (overlapDay != null) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            String.format("Xung đột phòng tập: Phòng '%s' đã có lớp '%s' học vào %s (%s - %s)",
                                    existing.getRoom().getName(),
                                    existing.getName(),
                                    overlapDay,
                                    existing.getStartTime(),
                                    existing.getEndTime())
                    );
                }
            }
        }

        List<ClassEntity> coachClasses = classRepository.findActiveClassesByCoachId(coachId);
        for (ClassEntity existing : coachClasses) {
            if (excludeClassId != null && existing.getId().equals(excludeClassId)) {
                continue;
            }
            if (isTimeOverlap(newStart, newEnd, existing.getStartTime(), existing.getEndTime())) {
                String overlapDay = findCommonDay(newDays, existing.getDaysOfWeek());
                if (overlapDay != null) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            String.format("Xung đột HLV: Huấn luyện viên '%s' đã có lịch dạy lớp '%s' vào %s (%s - %s)",
                                    existing.getCoach().getFullName(),
                                    existing.getName(),
                                    overlapDay,
                                    existing.getStartTime(),
                                    existing.getEndTime())
                    );
                }
            }
        }
    }

    private boolean isTimeOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) {
            return false;
        }
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private String findCommonDay(List<String> daysA, String daysBStr) {
        if (daysA == null || daysA.isEmpty() || daysBStr == null || daysBStr.isBlank()) {
            return null;
        }
        Set<String> setA = new HashSet<>();
        for (String d : daysA) {
            setA.add(d.trim().toUpperCase());
        }

        String[] parts = daysBStr.split(",");
        for (String p : parts) {
            String trimmed = p.trim().toUpperCase();
            if (setA.contains(trimmed)) {
                return p.trim();
            }
        }
        return null;
    }

    private String normalizeText(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseClassId(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String cleaned = keyword.trim().toUpperCase();
        if (cleaned.startsWith("CLS-")) {
            try {
                return Integer.parseInt(cleaned.substring(4));
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
