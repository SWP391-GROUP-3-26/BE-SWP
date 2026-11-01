package com.swp391.beswp.service;

import com.swp391.beswp.dto.SessionEvaluationDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionEvaluationService {
    private final TrainingResultRepository results;
    private final ClassRepository classes;
    private final ClassSessionRepository sessions;
    private final CoachAttendanceRepository attendance;
    private final UserRepository users;
    private final Clock paymentClock;

    public CoachView coachView(Authentication auth, Integer classId, Integer sessionId) {
        ClassEntity classroom = classroom(classId, actor(auth, "Coach"), false);
        return coachView(classroom, session(classId, sessionId));
    }

    @Transactional
    public CoachView save(Authentication auth, Integer classId, Integer sessionId, Integer memberId, SaveEvaluation input) {
        User coach = actor(auth, "Coach");
        ClassEntity classroom = classroom(classId, coach, true);
        ClassSession session = session(classId, sessionId);
        if (!canEvaluate(classroom, session)) throw error(HttpStatus.CONFLICT, "Chỉ đánh giá sau khi buổi học kết thúc; buổi hoặc lớp đã hủy không được đánh giá");
        User member = attendance.findRegisteredMembersForSession(sessionId).stream().filter(u -> u.getId().equals(memberId))
                .findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Học viên không có đăng ký hợp lệ trong buổi học"));
        boolean present = attendance.findBySession_Id(sessionId).stream().anyMatch(a -> a.getUser().getId().equals(memberId)
                && "Present".equalsIgnoreCase(a.getStatus()));
        if (!present) throw error(HttpStatus.CONFLICT, "Vui lòng lưu điểm danh Có mặt cho học viên trước khi đánh giá");
        TrainingResult existing = results.findBySession_IdAndMember_Id(sessionId, memberId).orElse(null);
        if (existing == null ? input.version() != null : !Objects.equals(input.version(), existing.getVersion())) {
            throw error(HttpStatus.CONFLICT, "Đánh giá đã thay đổi. Vui lòng tải lại trước khi lưu");
        }
        TrainingResult record = existing == null ? new TrainingResult() : existing;
        record.setSession(session); record.setMember(member); record.setCoach(coach); record.setDate(session.getDate());
        record.setTrainingContent(input.trainingContent().trim()); record.setResult(input.result().trim());
        record.setProgressStatus(input.progressStatus()); record.setCoachFeedback(input.coachFeedback().trim());
        record.setNextSteps(input.nextSteps() == null ? "" : input.nextSteps().trim());
        record.setUpdatedAt(LocalDateTime.now(paymentClock)); record.setWithdrawn(false);
        results.saveAndFlush(record);
        return coachView(classroom, session);
    }

    @Transactional
    public CoachView withdraw(Authentication auth, Integer classId, Integer sessionId, Integer memberId, WithdrawEvaluation input) {
        ClassEntity classroom = classroom(classId, actor(auth, "Coach"), true);
        ClassSession session = session(classId, sessionId);
        TrainingResult record = results.findBySession_IdAndMember_Id(sessionId, memberId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá"));
        if (!Objects.equals(input.version(), record.getVersion())) throw error(HttpStatus.CONFLICT, "Đánh giá đã thay đổi. Vui lòng tải lại");
        record.setWithdrawn(true); record.setUpdatedAt(LocalDateTime.now(paymentClock)); results.flush();
        return coachView(classroom, session);
    }

    public List<MemberSummary> memberSummaries(Authentication auth) {
        User member = actor(auth, "Member");
        return results.findByMember_IdAndWithdrawnFalseOrderByUpdatedAtDesc(member.getId()).stream()
                .map(r -> new MemberSummary(r.getSession().getId())).toList();
    }

    public EvaluationView memberDetail(Authentication auth, Integer sessionId) {
        User member = actor(auth, "Member");
        return results.findBySession_IdAndMember_Id(sessionId, member.getId()).filter(r -> !r.isWithdrawn())
                .map(this::view).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Chưa có đánh giá cho buổi học này"));
    }

    private CoachView coachView(ClassEntity classroom, ClassSession session) {
        List<TrainingResult> records = results.findBySession_ClassEntity_IdOrderBySession_DateDescSession_EndTimeDescIdDesc(classroom.getId());
        Map<Integer, TrainingResult> current = records.stream().filter(r -> r.getSession().getId().equals(session.getId()))
                .collect(Collectors.toMap(r -> r.getMember().getId(), Function.identity()));
        Map<Integer, String> marks = attendance.findBySession_Id(session.getId()).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), AttendanceRecord::getStatus));
        List<StudentView> students = attendance.findRegisteredMembersForSession(session.getId()).stream().map(member -> {
            TrainingResult result = current.get(member.getId());
            List<EvaluationView> history = records.stream().filter(r -> !r.isWithdrawn() && r.getMember().getId().equals(member.getId())
                    && r.getSession().getDate().atTime(r.getSession().getEndTime()).isBefore(session.getDate().atTime(session.getEndTime())))
                    .limit(3).map(this::view).toList();
            return new StudentView(member.getId(), member.getFullName(), marks.get(member.getId()), result == null ? null : view(result), history);
        }).toList();
        return new CoachView(session.getId(), session.getDate(), session.getStartTime(), session.getEndTime(), canEvaluate(classroom, session), students);
    }

    private EvaluationView view(TrainingResult r) {
        ClassSession s = r.getSession();
        return new EvaluationView(r.getId(), s.getId(), r.getMember().getId(), r.getMember().getFullName(), s.getClassEntity().getName(),
                r.getCoach().getFullName(), s.getDate(), s.getStartTime(), s.getEndTime(), r.getTrainingContent(), r.getResult(),
                r.getProgressStatus(), r.getCoachFeedback(), r.getNextSteps(), r.getUpdatedAt(), r.getVersion(), r.isWithdrawn());
    }

    private boolean canEvaluate(ClassEntity classroom, ClassSession session) {
        return !cancelled(classroom.getStatus()) && !cancelled(session.getStatus()) && ClassSchedule.withinTerm(classroom, session.getDate())
                && !LocalDateTime.now(paymentClock).isBefore(session.getDate().atTime(session.getEndTime()));
    }

    private boolean cancelled(String status) { return "Cancelled".equalsIgnoreCase(status) || "Cancel".equalsIgnoreCase(status); }

    private ClassEntity classroom(Integer id, User coach, boolean lock) {
        ClassEntity c = (lock ? classes.findByIdForUpdate(id) : classes.findById(id))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy lớp"));
        if (c.getCoach() == null || !c.getCoach().getId().equals(coach.getId())) throw error(HttpStatus.FORBIDDEN, "Bạn không phụ trách lớp này");
        return c;
    }

    private ClassSession session(Integer classId, Integer id) {
        ClassSession s = sessions.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy buổi học"));
        if (!s.getClassEntity().getId().equals(classId)) throw error(HttpStatus.NOT_FOUND, "Buổi học không thuộc lớp này");
        return s;
    }

    private User actor(Authentication auth, String role) {
        if (auth == null || !auth.isAuthenticated()) throw error(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        if (auth.getAuthorities().stream().noneMatch(a -> ("ROLE_" + role).equals(a.getAuthority()))) throw error(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
        int id;
        try { id = Integer.parseInt(auth.getName()); } catch (NumberFormatException ex) { throw error(HttpStatus.UNAUTHORIZED, "Danh tính không hợp lệ"); }
        User user = users.findById(id).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
        if (user.getRole() == null || !role.equalsIgnoreCase(user.getRole().getRoleName()) || !"Active".equalsIgnoreCase(user.getStatus())) throw error(HttpStatus.FORBIDDEN, "Tài khoản không hoạt động hoặc không đúng vai trò");
        return user;
    }

    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
