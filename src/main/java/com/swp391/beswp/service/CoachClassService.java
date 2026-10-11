package com.swp391.beswp.service;

import com.swp391.beswp.dto.CoachClassDetailResponse;
import com.swp391.beswp.dto.CoachClassItemResponse;
import com.swp391.beswp.dto.CoachClassListResponse;
import com.swp391.beswp.dto.CoachClassStudentResponse;
import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CoachClassService {

    private final ClassRepository classRepository;
    private final ClassBookingRepository classBookingRepository;
    private final UserRepository userRepository;

    /**
     * Lấy danh sách các lớp học hiện tại đang phụ trách/dạy của Huấn luyện viên (Coach).
     */
    @Transactional(readOnly = true)
    public CoachClassListResponse getCoachClasses(String principal, String status, String search) {
        User coach = findCoachUser(principal);

        String resolvedStatus = resolveClassStatusQuery(status);
        String keyword = normalizeText(search);
        Integer parsedId = parseClassId(keyword);

        List<ClassEntity> classes = classRepository.findClassesByCoach(
                coach.getId(),
                resolvedStatus,
                StringUtils.hasText(keyword) ? keyword.toLowerCase(Locale.ROOT) : null,
                parsedId
        );

        List<CoachClassItemResponse> items = new ArrayList<>();
        for (ClassEntity c : classes) {
            long enrolledCount = classBookingRepository.countActiveMembersByClassId(c.getId());
            int availableSlots = Math.max(0, c.getMaxCapacity() - (int) enrolledCount);
            String schedule = formatScheduleWithDays(c.getStartTime(), c.getEndTime(), c.getDaysOfWeek());
            String displayStatus = mapClassDisplayStatus(c.getStatus());

            items.add(CoachClassItemResponse.builder()
                    .classId(c.getId())
                    .classCode(String.format("CLS-%03d", c.getId()))
                    .name(c.getName())
                    .subjectId(c.getSubject() != null ? c.getSubject().getId() : null)
                    .subjectName(c.getSubject() != null ? c.getSubject().getName() : "")
                    .subjectCategory(c.getSubject() != null ? c.getSubject().getCategory() : "")
                    .roomId(c.getRoom() != null ? c.getRoom().getId() : null)
                    .roomName(c.getRoom() != null ? c.getRoom().getName() : "")
                    .roomMaxCapacity(c.getRoom() != null ? c.getRoom().getMaxCapacity() : null)
                    .maxCapacity(c.getMaxCapacity())
                    .enrolledStudentsCount((int) enrolledCount)
                    .availableSlots(availableSlots)
                    .schedule(schedule)
                    .daysOfWeek(c.getDaysOfWeek())
                    .startTime(c.getStartTime())
                    .endTime(c.getEndTime())
                    .startDate(c.getDate())
                    .price(c.getPrice())
                    .status(c.getStatus())
                    .statusDisplay(displayStatus)
                    .build());
        }

        long totalClasses = classRepository.countByCoach_Id(coach.getId());
        long activeClasses = classRepository.countActiveClassesByCoachId(coach.getId());
        long completedClasses = classRepository.countCompletedClassesByCoachId(coach.getId());
        long cancelledClasses = classRepository.countCancelledClassesByCoachId(coach.getId());
        long totalStudents = classBookingRepository.countTotalActiveStudentsByCoachId(coach.getId());

        return CoachClassListResponse.builder()
                .success(true)
                .message("Lấy danh sách lớp học của huấn luyện viên thành công")
                .coachId(coach.getId())
                .coachName(coach.getFullName())
                .counts(CoachClassListResponse.Counts.builder()
                        .totalClasses(totalClasses)
                        .activeClasses(activeClasses)
                        .completedClasses(completedClasses)
                        .cancelledClasses(cancelledClasses)
                        .totalStudents(totalStudents)
                        .build())
                .classes(items)
                .build();
    }

    /**
     * Xem thông tin chi tiết một lớp học của Coach kèm danh sách học viên đang theo học.
     */
    @Transactional(readOnly = true)
    public CoachClassDetailResponse getCoachClassDetail(String principal, Integer classId) {
        User coach = findCoachUser(principal);

        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học với mã ID: " + classId));

        boolean isAssignedCoach = classEntity.getCoach() != null && classEntity.getCoach().getId().equals(coach.getId());
        boolean isManagerOrAdmin = coach.getRole() != null &&
                ("Admin".equalsIgnoreCase(coach.getRole().getRoleName()) || "Center Manager".equalsIgnoreCase(coach.getRole().getRoleName()));

        if (!isAssignedCoach && !isManagerOrAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông tin lớp học này vì không phải là huấn luyện viên phụ trách.");
        }

        List<ClassBooking> bookings = classBookingRepository.findEnrolledStudentsByClassId(classEntity.getId());
        List<CoachClassStudentResponse> students = bookings.stream().map(b -> {
            User u = b.getUser();
            String rawStatus = b.getStatus();
            String displayStatus = "Completed".equalsIgnoreCase(rawStatus) ? "Đã hoàn thành" : "Đang theo học";

            return CoachClassStudentResponse.builder()
                    .userId(u != null ? u.getId() : null)
                    .fullName(u != null ? u.getFullName() : "Học viên")
                    .username(u != null ? u.getUsername() : "")
                    .phone(u != null ? u.getPhone() : "")
                    .email(u != null ? u.getEmail() : "")
                    .gender(u != null ? u.getGender() : "")
                    .avatarUrl(u != null ? u.getAvatarUrl() : null)
                    .bookingDatetime(b.getBookingDatetime())
                    .status(rawStatus)
                    .statusDisplay(displayStatus)
                    .build();
        }).toList();

        int enrolledCount = students.size();
        int availableSlots = Math.max(0, classEntity.getMaxCapacity() - enrolledCount);
        String schedule = formatScheduleWithDays(classEntity.getStartTime(), classEntity.getEndTime(), classEntity.getDaysOfWeek());

        return CoachClassDetailResponse.builder()
                .success(true)
                .message("Lấy thông tin chi tiết lớp học thành công")
                .classId(classEntity.getId())
                .classCode(String.format("CLS-%03d", classEntity.getId()))
                .name(classEntity.getName())
                .subjectId(classEntity.getSubject() != null ? classEntity.getSubject().getId() : null)
                .subjectName(classEntity.getSubject() != null ? classEntity.getSubject().getName() : "")
                .subjectCategory(classEntity.getSubject() != null ? classEntity.getSubject().getCategory() : "")
                .subjectDescription(classEntity.getSubject() != null ? classEntity.getSubject().getDescription() : "")
                .roomId(classEntity.getRoom() != null ? classEntity.getRoom().getId() : null)
                .roomName(classEntity.getRoom() != null ? classEntity.getRoom().getName() : "")
                .roomMaxCapacity(classEntity.getRoom() != null ? classEntity.getRoom().getMaxCapacity() : null)
                .maxCapacity(classEntity.getMaxCapacity())
                .enrolledStudentsCount(enrolledCount)
                .availableSlots(availableSlots)
                .schedule(schedule)
                .daysOfWeek(classEntity.getDaysOfWeek())
                .startTime(classEntity.getStartTime())
                .endTime(classEntity.getEndTime())
                .startDate(classEntity.getDate())
                .price(classEntity.getPrice())
                .status(classEntity.getStatus())
                .statusDisplay(mapClassDisplayStatus(classEntity.getStatus()))
                .students(students)
                .build();
    }

    private User findCoachUser(String principal) {
        if (!StringUtils.hasText(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện");
        }

        User user;
        try {
            Integer userId = Integer.valueOf(principal);
            user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin tài khoản"));
        } catch (NumberFormatException ignored) {
            user = userRepository.findByUsernameIgnoreCase(principal)
                    .or(() -> userRepository.findByEmailIgnoreCase(principal))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin tài khoản"));
        }

        if (user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa được phân quyền");
        }

        String role = user.getRole().getRoleName();
        if (!"Coach".equalsIgnoreCase(role) && !"Admin".equalsIgnoreCase(role) && !"Center Manager".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chức năng này chỉ dành cho Huấn luyện viên (Coach)");
        }

        return user;
    }

    private String resolveClassStatusQuery(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status) || "tất cả".equalsIgnoreCase(status)) {
            return null;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        if (s.contains("active") || s.contains("đang") || s.contains("dang") || s.contains("open")) {
            return "Active";
        }
        if (s.contains("complete") || s.contains("hoàn thành") || s.contains("hoan thanh")) {
            return "Complete";
        }
        if (s.contains("cancel") || s.contains("hủy") || s.contains("huy")) {
            return "Cancelled";
        }
        return status.trim();
    }

    private String mapClassDisplayStatus(String raw) {
        if (raw == null) return "Đang diễn ra";
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "active" -> "Đang diễn ra";
            case "open" -> "Đang mở đăng ký";
            case "closed" -> "Đã đóng";
            case "complete", "completed" -> "Đã hoàn thành";
            case "cancel", "cancelled" -> "Đã hủy";
            default -> raw;
        };
    }

    private String formatScheduleWithDays(LocalTime start, LocalTime end, String days) {
        String range = formatTimeRange(start, end);
        if (StringUtils.hasText(days)) {
            return String.format("%s (%s)", range, days.trim());
        }
        return range;
    }

    private String formatTimeRange(LocalTime start, LocalTime end) {
        if (start != null && end != null) {
            return String.format("%02d:%02d - %02d:%02d", start.getHour(), start.getMinute(), end.getHour(), end.getMinute());
        }
        return "Linh hoạt";
    }

    private String normalizeText(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseClassId(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        String cleaned = keyword.trim().toUpperCase(Locale.ROOT);
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
