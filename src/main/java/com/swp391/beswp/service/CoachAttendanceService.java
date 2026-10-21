package com.swp391.beswp.service;

import com.swp391.beswp.dto.CoachAttendanceDtos.AttendanceEntry;
import com.swp391.beswp.dto.CoachAttendanceDtos.AttendanceResponse;
import com.swp391.beswp.dto.CoachAttendanceDtos.MemberAttendance;
import com.swp391.beswp.dto.CoachAttendanceDtos.SaveAttendanceRequest;
import com.swp391.beswp.dto.CoachAttendanceDtos.SessionAttendance;
import com.swp391.beswp.dto.CoachAttendanceDtos.SessionSummary;
import com.swp391.beswp.dto.CoachAttendanceDtos.SessionsResponse;
import com.swp391.beswp.entity.AttendanceRecord;
import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.ClassSession;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.ClassSessionRepository;
import com.swp391.beswp.repository.CoachAttendanceRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoachAttendanceService {

    private static final Set<String> ALLOWED_ATTENDANCE_STATUSES =
            Set.of("Present", "Absent", "Late", "Excused");

    private final ClassRepository classRepository;
    private final ClassSessionRepository classSessionRepository;
    private final CoachAttendanceRepository coachAttendanceRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SessionsResponse getClassSessions(Authentication authentication, Integer classId) {
        User coach = requireActiveCoach(authentication);
        ClassEntity classEntity = findClass(classId);
        requireAssignedCoach(classEntity, coach);

        List<ClassSession> sessions = classSessionRepository.findByClassEntity_Id(classId).stream()
                .sorted(Comparator.comparing(ClassSession::getDate)
                        .thenComparing(ClassSession::getStartTime)
                        .thenComparing(ClassSession::getId))
                .toList();

        if (sessions.isEmpty()) {
            return new SessionsResponse(true, "Lớp học chưa có buổi học", List.of());
        }

        Map<Integer, Set<Integer>> rosterBySession = new HashMap<>();
        for (ClassBooking booking : coachAttendanceRepository.findEnrolledBookingsForClass(classId)) {
            if (isEnrolled(booking) && isMember(booking.getUser())) {
                rosterBySession.computeIfAbsent(booking.getSession().getId(), ignored -> new HashSet<>())
                        .add(booking.getUser().getId());
            }
        }

        List<Integer> sessionIds = sessions.stream().map(ClassSession::getId).toList();
        Map<Integer, List<AttendanceRecord>> attendanceBySession = coachAttendanceRepository
                .findBySession_IdIn(sessionIds).stream()
                .collect(Collectors.groupingBy(record -> record.getSession().getId()));

        List<SessionSummary> data = sessions.stream()
                .map(session -> summary(
                        session,
                        rosterBySession.getOrDefault(session.getId(), Set.of()),
                        attendanceBySession.getOrDefault(session.getId(), List.of())
                ))
                .toList();

        return new SessionsResponse(true, "Lấy danh sách buổi học và thống kê điểm danh thành công", data);
    }

    @Transactional(readOnly = true)
    public AttendanceResponse getSessionAttendance(
            Authentication authentication,
            Integer classId,
            Integer sessionId
    ) {
        User coach = requireActiveCoach(authentication);
        ClassEntity classEntity = findClass(classId);
        requireAssignedCoach(classEntity, coach);
        ClassSession session = findSession(classId, sessionId);

        List<User> members = coachAttendanceRepository.findRegisteredMembersForSession(sessionId);
        List<AttendanceRecord> attendance = coachAttendanceRepository.findBySession_Id(sessionId);
        return new AttendanceResponse(true, "Lấy danh sách hội viên và điểm danh thành công",
                toSessionAttendance(session, members, attendance));
    }

    @Transactional
    public AttendanceResponse saveSessionAttendance(
            Authentication authentication,
            Integer classId,
            Integer sessionId,
            SaveAttendanceRequest request
    ) {
        User coach = requireActiveCoach(authentication);
        validateIds(classId, sessionId);

        // Lock the class row first so concurrent attendance writes for the same class serialize.
        ClassEntity classEntity = classRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học"));
        requireAssignedCoach(classEntity, coach);

        ClassSession session = findSession(classId, sessionId);
        if ("cancelled".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể điểm danh buổi học đã bị hủy");
        }
        if ("cancelled".equalsIgnoreCase(classEntity.getStatus())
                || "cancel".equalsIgnoreCase(classEntity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể điểm danh lớp học đã bị hủy");
        }
        if (request == null || request.records() == null || request.records().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách điểm danh không được để trống");
        }

        List<User> members = coachAttendanceRepository.findRegisteredMembersForSession(sessionId);
        if (members.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Buổi học chưa có hội viên đăng ký hợp lệ");
        }

        Map<Integer, User> rosterByMemberId = members.stream()
                .collect(Collectors.toMap(User::getId, member -> member));
        Map<Integer, AttendanceEntry> requestByMemberId = new LinkedHashMap<>();
        for (AttendanceEntry entry : request.records()) {
            if (entry == null || entry.memberId() == null || entry.memberId() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mỗi bản ghi phải có memberId hợp lệ");
            }
            if (requestByMemberId.putIfAbsent(entry.memberId(), entry) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Mỗi hội viên chỉ được xuất hiện một lần trong danh sách điểm danh");
            }
            String normalizedStatus = normalizeStatus(entry.status());
            if (normalizedStatus == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Trạng thái chỉ nhận Present, Absent, Late hoặc Excused");
            }
        }

        if (!requestByMemberId.keySet().equals(rosterByMemberId.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Danh sách phải bao gồm chính xác tất cả hội viên đang đăng ký buổi học");
        }

        List<AttendanceRecord> existingRecords = coachAttendanceRepository.findBySession_Id(sessionId);
        Map<Integer, AttendanceRecord> attendanceByMemberId = existingRecords.stream()
                .collect(Collectors.toMap(record -> record.getUser().getId(), record -> record, (left, right) -> left));

        List<AttendanceRecord> updates = new ArrayList<>(members.size());
        for (User member : members) {
            AttendanceEntry entry = requestByMemberId.get(member.getId());
            AttendanceRecord record = attendanceByMemberId.get(member.getId());
            if (record == null) {
                record = AttendanceRecord.builder().session(session).user(member).build();
            }
            record.setStatus(normalizeStatus(entry.status()));
            record.setNote(normalizeNote(entry.note()));
            updates.add(record);
        }

        List<AttendanceRecord> saved = coachAttendanceRepository.saveAll(updates);
        return new AttendanceResponse(true, "Lưu điểm danh thành công",
                toSessionAttendance(session, members, saved));
    }

    private SessionSummary summary(
            ClassSession session,
            Set<Integer> rosterMemberIds,
            List<AttendanceRecord> records
    ) {
        Map<String, Long> counts = records.stream()
                .filter(record -> rosterMemberIds.contains(record.getUser().getId()))
                .filter(record -> normalizeStatus(record.getStatus()) != null)
                .collect(Collectors.groupingBy(
                        record -> normalizeStatus(record.getStatus()),
                        Collectors.counting()
                ));
        int marked = counts.values().stream().mapToInt(Long::intValue).sum();
        return new SessionSummary(
                session.getId(), session.getDate(), session.getStartTime(), session.getEndTime(), session.getStatus(),
                rosterMemberIds.size(), marked,
                count(counts, "Present"), count(counts, "Absent"), count(counts, "Late"), count(counts, "Excused")
        );
    }

    private SessionAttendance toSessionAttendance(
            ClassSession session,
            List<User> members,
            List<AttendanceRecord> records
    ) {
        Map<Integer, AttendanceRecord> attendanceByMemberId = records.stream()
                .collect(Collectors.toMap(record -> record.getUser().getId(), record -> record, (left, right) -> left));
        Set<Integer> memberIds = members.stream().map(User::getId).collect(Collectors.toSet());
        List<AttendanceRecord> rosterRecords = records.stream()
                .filter(record -> memberIds.contains(record.getUser().getId()))
                .toList();
        SessionSummary sessionSummary = summary(session, memberIds, rosterRecords);

        List<MemberAttendance> memberData = members.stream()
                .map(member -> {
                    AttendanceRecord record = attendanceByMemberId.get(member.getId());
                    return new MemberAttendance(
                            member.getId(), member.getFullName(), member.getUsername(), member.getPhone(),
                            member.getAvatarUrl(), record == null ? null : record.getStatus(),
                            record == null ? null : record.getNote()
                    );
                })
                .toList();
        return new SessionAttendance(sessionSummary, memberData);
    }

    private int count(Map<String, Long> counts, String status) {
        return counts.getOrDefault(status, 0L).intValue();
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = ALLOWED_ATTENDANCE_STATUSES.stream()
                .filter(candidate -> candidate.equalsIgnoreCase(status.trim()))
                .findFirst()
                .orElse(null);
        return normalized;
    }

    private String normalizeNote(String note) {
        return StringUtils.hasText(note) ? note.trim() : null;
    }

    private boolean isEnrolled(ClassBooking booking) {
        return booking != null && booking.getUser() != null && booking.getSession() != null
                && ("Booked".equalsIgnoreCase(booking.getStatus())
                || "Completed".equalsIgnoreCase(booking.getStatus()));
    }

    private boolean isMember(User user) {
        return user != null && user.getRole() != null
                && "Member".equalsIgnoreCase(user.getRole().getRoleName());
    }

    private User requireActiveCoach(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện");
        }
        boolean hasCoachAuthority = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_Coach".equals(authority.getAuthority()));
        if (!hasCoachAuthority) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chức năng này chỉ dành cho Coach");
        }

        final Integer coachId;
        try {
            coachId = Integer.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Danh tính Coach không hợp lệ");
        }

        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản Coach"));
        if (!isMemberOfRole(coach, "Coach")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không có vai trò Coach");
        }
        if (!"Active".equalsIgnoreCase(coach.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản Coach không hoạt động");
        }
        return coach;
    }

    private boolean isMemberOfRole(User user, String role) {
        return user.getRole() != null && role.equalsIgnoreCase(user.getRole().getRoleName());
    }

    private void validateIds(Integer classId, Integer sessionId) {
        if (classId == null || classId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "classId không hợp lệ");
        }
        if (sessionId == null || sessionId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId không hợp lệ");
        }
    }

    private ClassEntity findClass(Integer classId) {
        if (classId == null || classId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "classId không hợp lệ");
        }
        return classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học"));
    }

    private ClassSession findSession(Integer classId, Integer sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId không hợp lệ");
        }
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy buổi học"));
        if (session.getClassEntity() == null || !classId.equals(session.getClassEntity().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Buổi học không thuộc lớp đã chọn");
        }
        return session;
    }

    private void requireAssignedCoach(ClassEntity classEntity, User coach) {
        if (classEntity.getCoach() == null || !coach.getId().equals(classEntity.getCoach().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn không phải huấn luyện viên phụ trách lớp học này");
        }
    }
}
