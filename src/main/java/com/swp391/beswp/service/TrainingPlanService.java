package com.swp391.beswp.service;

import com.swp391.beswp.dto.TrainingPlanDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingPlanService {
    private final TrainingPlanRepository plans;
    private final ClassRepository classes;
    private final ClassBookingRepository bookings;
    private final UserRepository users;
    private final Clock paymentClock;
    private final MemberNotificationService memberNotifications;

    public List<PlanView> coachPlans(Authentication auth) {
        User coach = actor(auth, "Coach");
        return plans.findByClassEntity_Coach_IdOrderByUpdatedAtDesc(coach.getId()).stream()
                .map(p -> view(p, false)).toList();
    }

    public List<PlanView> memberPlans(Authentication auth) {
        User member = actor(auth, "Member");
        return plans.findVisibleToMember(member.getId()).stream().map(p -> view(p, false, member.getId())).toList();
    }

    public PlanView detail(Authentication auth, Integer id, boolean coach) {
        User user = actor(auth, coach ? "Coach" : "Member");
        TrainingPlan plan = find(id);
        authorize(plan, user, coach);
        return view(plan, true, coach ? null : user.getId());
    }

    @Transactional
    public PlanView create(Authentication auth, SavePlan input) {
        User coach = actor(auth, "Coach");
        ClassEntity c = assignedClass(input.classId(), coach);
        String scope = scope(input);
        User member = null;
        if ("INDIVIDUAL".equals(scope)) {
            member = users.findById(input.memberId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy học viên"));
            requireEnrollment(c, member);
        } else requireClassActive(c);
        TrainingPlan plan = new TrainingPlan();
        plan.setClassEntity(c);
        plan.setScope(scope);
        plan.setMember(member);
        plan.setCreatedBy(coach);
        plan.setCreatedAt(now());
        apply(plan, input);
        TrainingPlan saved = plans.saveAndFlush(plan);
        if (saved.getPublishedRevision() > 0) {
            memberNotifications.notifyTrainingPlan(saved, notificationRecipients(saved));
        }
        return view(saved, true);
    }

    @Transactional
    public PlanView update(Authentication auth, Integer id, SavePlan input) {
        User coach = actor(auth, "Coach");
        // Serializing changes within a class also prevents two drafts being assigned simultaneously.
        assignedClass(input.classId(), coach);
        TrainingPlan plan = findForWrite(id);
        authorize(plan, coach, true);
        checkVersion(plan, input.version());
        if (!plan.getClassEntity().getId().equals(input.classId()) || !Objects.equals(
                plan.getMember() == null ? null : plan.getMember().getId(), input.memberId()) || !plan.getScope().equals(scope(input))) {
            throw error(HttpStatus.BAD_REQUEST, "Không thể đổi lớp, loại hoặc học viên của kế hoạch");
        }
        requireMutable(plan);
        if (plan.getPublishedRevision() > 0 && !input.publish()) {
            throw error(HttpStatus.BAD_REQUEST, "Kế hoạch đã giao không thể chuyển về bản nháp");
        }
        int previousRevision = plan.getPublishedRevision();
        apply(plan, input);
        plans.flush();
        if (plan.getPublishedRevision() > previousRevision) {
            memberNotifications.notifyTrainingPlan(plan, notificationRecipients(plan));
        }
        return view(plan, true);
    }

    @Transactional
    public PlanView review(Authentication auth, Integer id, ReviewPlan input) {
        User coach = actor(auth, "Coach");
        TrainingPlan plan = findForWrite(id);
        authorize(plan, coach, true);
        checkVersion(plan, input.version());
        requireMutable(plan);
        if ("DRAFT".equals(plan.getStatus()) && !"CANCELLED".equals(input.status())) {
            throw error(HttpStatus.BAD_REQUEST, "Vui lòng giao kế hoạch trước khi đánh giá");
        }
        plan.setStatus(input.status());
        plan.setCoachFeedback(trim(input.coachFeedback()));
        plan.setUpdatedAt(now());
        plans.flush();
        return view(plan, true);
    }

    @Transactional
    public PlanView recordResult(Authentication auth, Integer id, Integer exerciseId, ExerciseResult input) {
        User member = actor(auth, "Member");
        TrainingPlan plan = findForWrite(id);
        authorize(plan, member, false);
        checkVersion(plan, input.version());
        if (!canRecord(plan, member)) throw error(HttpStatus.CONFLICT, "Kế hoạch hiện không cho phép ghi nhận kết quả");
        TrainingExercise exercise = plan.getExercises().stream().filter(e -> e.getId().equals(exerciseId))
                .findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Bài tập không thuộc kế hoạch"));
        if (isClass(plan)) {
            TrainingPlanRecipient recipient = recipient(plan, member.getId());
            TrainingExerciseResult result = recipient.getResults().stream()
                    .filter(r -> r.getExercise().getId().equals(exerciseId)).findFirst().orElseGet(() -> {
                        TrainingExerciseResult created = new TrainingExerciseResult();
                        created.setRecipient(recipient); created.setExercise(exercise); recipient.getResults().add(created);
                        return created;
                    });
            result.setCompleted(input.completed()); result.setMemberResult(trim(input.memberResult()));
        } else {
            exercise.setCompleted(input.completed()); exercise.setMemberResult(trim(input.memberResult()));
        }
        plan.setUpdatedAt(now());
        plans.flush();
        return view(plan, true, member.getId());
    }

    @Transactional
    public PlanView markRead(Authentication auth, Integer id, int revision) {
        User member = actor(auth, "Member");
        TrainingPlan plan = findForWrite(id);
        authorize(plan, member, false);
        if (revision < 1 || revision > plan.getPublishedRevision()) {
            throw error(HttpStatus.BAD_REQUEST, "Phiên bản thông báo không hợp lệ");
        }
        if (isClass(plan)) {
            TrainingPlanRecipient recipient = recipient(plan, member.getId());
            recipient.setReadRevision(Math.max(recipient.getReadRevision(), revision));
        } else plan.setReadRevision(Math.max(plan.getReadRevision(), revision));
        plans.flush();
        return view(plan, true, member.getId());
    }

    public List<MemberOption> eligibleMembers(Authentication auth, Integer classId) {
        User coach = actor(auth, "Coach");
        ClassEntity c = classes.findById(classId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy lớp"));
        if (c.getCoach() == null || !c.getCoach().getId().equals(coach.getId())) throw error(HttpStatus.FORBIDDEN, "Bạn không phụ trách lớp này");
        requireClassActive(c);
        return bookings.findTrainingPlanMembers(classId).stream().map(u -> new MemberOption(u.getId(), u.getFullName())).toList();
    }

    @Transactional
    public PlanView addRecipients(Authentication auth, Integer id, VersionInput input) {
        User coach = actor(auth, "Coach");
        TrainingPlan plan = findForWrite(id);
        authorize(plan, coach, true); checkVersion(plan, input.version()); requireMutable(plan);
        if (!isClass(plan) || !"ASSIGNED".equals(plan.getStatus()) || today().isAfter(plan.getEndDate()))
            throw error(HttpStatus.CONFLICT, "Chỉ bổ sung học viên vào kế hoạch cả lớp đã giao và chưa hết hạn");
        List<User> newRecipients = addCurrentMembers(plan);
        plan.setUpdatedAt(now()); plans.flush();
        memberNotifications.notifyTrainingPlan(plan, newRecipients);
        return view(plan, true);
    }

    @Transactional
    public PlanView reviewRecipient(Authentication auth, Integer id, Integer memberId, RecipientFeedback input) {
        User coach = actor(auth, "Coach");
        TrainingPlan plan = findForWrite(id);
        authorize(plan, coach, true); checkVersion(plan, input.version()); requireMutable(plan);
        if (!isClass(plan) || !"ASSIGNED".equals(plan.getStatus())) throw error(HttpStatus.CONFLICT, "Kế hoạch không cho phép nhận xét học viên");
        recipient(plan, memberId).setCoachFeedback(trim(input.coachFeedback()));
        plan.setUpdatedAt(now()); plans.flush();
        return view(plan, true);
    }

    private void apply(TrainingPlan plan, SavePlan input) {
        ClassEntity c = plan.getClassEntity();
        if (isClass(plan)) requireClassActive(c);
        else requireEnrollment(c, plan.getMember());
        if (input.endDate().isBefore(input.startDate()) || input.endDate().isBefore(today())
                || !ClassSchedule.withinTerm(c, input.startDate()) || !ClassSchedule.withinTerm(c, input.endDate())) {
            throw error(HttpStatus.BAD_REQUEST, "Thời gian kế hoạch phải nằm trong thời hạn lớp và chưa kết thúc");
        }
        Map<Integer, TrainingExercise> existing = plan.getExercises().stream()
                .collect(Collectors.toMap(TrainingExercise::getId, Function.identity()));
        Set<Integer> retained = new HashSet<>();
        List<TrainingExercise> next = new ArrayList<>();
        for (ExerciseInput item : input.exercises()) {
            TrainingExercise exercise;
            if (item.id() == null) {
                exercise = new TrainingExercise();
                exercise.setPlan(plan);
                // Preserve the legacy non-null column; availability follows the plan's date range.
                exercise.setTrainingDate(input.startDate());
            } else {
                exercise = existing.get(item.id());
                if (exercise == null || !retained.add(item.id())) {
                    throw error(HttpStatus.BAD_REQUEST, "Mã bài tập không hợp lệ hoặc bị trùng");
                }
            }
            exercise.setPosition(next.size());
            exercise.setName(item.name().trim());
            exercise.setInstructions(trim(item.instructions()));
            exercise.setTarget(item.target().trim());
            next.add(exercise);
        }
        for (TrainingExercise old : plan.getExercises()) {
            if (!retained.contains(old.getId()) && hasResults(plan, old)) {
                throw error(HttpStatus.CONFLICT, "Không thể xóa bài tập đã có kết quả của học viên");
            }
        }
        plan.getExercises().removeIf(e -> !next.contains(e));
        for (TrainingExercise e : next) if (!plan.getExercises().contains(e)) plan.getExercises().add(e);
        plan.setTitle(input.title().trim());
        plan.setGoal(input.goal().trim());
        plan.setAssessment(trim(input.assessment()));
        plan.setStartDate(input.startDate());
        plan.setEndDate(input.endDate());
        plan.setUpdatedAt(now());
        if (input.publish()) {
            int excludedId = plan.getId() == null ? -1 : plan.getId();
            boolean duplicate = isClass(plan)
                    ? plans.existsByClassEntity_IdAndScopeAndStatusAndIdNot(c.getId(), "CLASS", "ASSIGNED", excludedId)
                    : plans.existsByClassEntity_IdAndMember_IdAndStatusAndIdNot(c.getId(), plan.getMember().getId(), "ASSIGNED", excludedId);
            if (duplicate) throw error(HttpStatus.CONFLICT, isClass(plan)
                    ? "Lớp đã có kế hoạch chung đang áp dụng" : "Học viên đã có kế hoạch cá nhân đang áp dụng trong lớp này");
            if (isClass(plan) && plan.getPublishedRevision() == 0) {
                addCurrentMembers(plan);
                if (plan.getRecipients().isEmpty()) throw error(HttpStatus.CONFLICT, "Lớp chưa có học viên đủ điều kiện nhận kế hoạch");
            }
            plan.setStatus("ASSIGNED");
            plan.setPublishedRevision(plan.getPublishedRevision() + 1);
            plan.setPublishedAt(now());
        }
    }

    private User actor(Authentication auth, String role) {
        if (auth == null || !auth.isAuthenticated()) throw error(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        if (auth.getAuthorities().stream().noneMatch(a -> ("ROLE_" + role).equals(a.getAuthority()))) {
            throw error(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện chức năng này");
        }
        int id;
        try { id = Integer.parseInt(auth.getName()); }
        catch (NumberFormatException ex) { throw error(HttpStatus.UNAUTHORIZED, "Danh tính không hợp lệ"); }
        User user = users.findById(id).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
        if (!hasRole(user, role) || !"Active".equalsIgnoreCase(user.getStatus())) {
            throw error(HttpStatus.FORBIDDEN, "Tài khoản không hoạt động hoặc không đúng vai trò");
        }
        return user;
    }

    private ClassEntity assignedClass(Integer id, User coach) {
        ClassEntity c = classes.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy lớp"));
        if (c.getCoach() == null || !c.getCoach().getId().equals(coach.getId())) {
            throw error(HttpStatus.FORBIDDEN, "Bạn không phải coach phụ trách lớp");
        }
        return c;
    }

    private TrainingPlan find(Integer id) {
        return plans.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch"));
    }

    private TrainingPlan findForWrite(Integer id) {
        Integer classId = plans.findClassId(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch thuộc lớp"));
        // Keep the same class -> plan lock order for publishing and individual result writes.
        classes.findByIdForUpdate(classId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy lớp"));
        return plans.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch"));
    }

    private void authorize(TrainingPlan p, User user, boolean coach) {
        if (p.getClassEntity() == null) throw error(HttpStatus.NOT_FOUND, "Kế hoạch cũ chưa được liên kết với lớp");
        boolean allowed = coach ? p.getClassEntity().getCoach() != null && p.getClassEntity().getCoach().getId().equals(user.getId())
                : p.getPublishedRevision() > 0 && (isClass(p)
                    ? p.getRecipients().stream().anyMatch(r -> r.getMember().getId().equals(user.getId()))
                    : p.getMember().getId().equals(user.getId()));
        if (!allowed) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch");
    }

    private void checkVersion(TrainingPlan p, Long version) {
        if (version == null || p.getVersion() != version) {
            throw error(HttpStatus.CONFLICT, "Kế hoạch đã thay đổi. Vui lòng tải lại trước khi lưu");
        }
    }

    private boolean enrollmentValid(TrainingPlan p) {
        return enrollmentValid(p, p.getMember());
    }

    private boolean enrollmentValid(TrainingPlan p, User member) {
        return classActive(p.getClassEntity()) && hasRole(member, "Member") && "Active".equalsIgnoreCase(member.getStatus())
                && bookings.isTrainingPlanMember(p.getClassEntity().getId(), member.getId());
    }

    private boolean classActive(ClassEntity c) { return ClassSchedule.isOpen(c) && !ClassSchedule.expired(c, today()); }

    private void requireClassActive(ClassEntity c) {
        if (!classActive(c)) throw error(HttpStatus.CONFLICT, "Lớp không còn hoạt động");
    }

    private void requireEnrollment(ClassEntity c, User member) {
        if (!ClassSchedule.isOpen(c) || ClassSchedule.expired(c, today())) {
            throw error(HttpStatus.CONFLICT, "Lớp không còn hoạt động");
        }
        if (!hasRole(member, "Member") || !"Active".equalsIgnoreCase(member.getStatus())
                || !bookings.isTrainingPlanMember(c.getId(), member.getId())) {
            throw error(HttpStatus.CONFLICT, "Học viên không có đăng ký hợp lệ trong lớp này");
        }
    }

    private boolean editable(TrainingPlan p) {
        return ("DRAFT".equals(p.getStatus()) || "ASSIGNED".equals(p.getStatus()))
                && (isClass(p) ? classActive(p.getClassEntity()) : enrollmentValid(p));
    }

    private void requireMutable(TrainingPlan p) {
        if (!editable(p)) throw error(HttpStatus.CONFLICT, "Kế hoạch hiện chỉ được xem lịch sử");
    }

    private boolean canRecord(TrainingPlan p, User member) {
        return "ASSIGNED".equals(p.getStatus()) && !today().isBefore(p.getStartDate())
                && !today().isAfter(p.getEndDate()) && enrollmentValid(p, member);
    }

    private boolean isClass(TrainingPlan p) { return "CLASS".equals(p.getScope()); }

    private String scope(SavePlan input) {
        String value = input.scope() == null ? "INDIVIDUAL" : input.scope();
        if (!Set.of("CLASS", "INDIVIDUAL").contains(value) || !input.isAudienceValid()) {
            throw error(HttpStatus.BAD_REQUEST, "Loại kế hoạch hoặc học viên không hợp lệ");
        }
        return value;
    }

    private TrainingPlanRecipient recipient(TrainingPlan p, Integer memberId) {
        return p.getRecipients().stream().filter(r -> r.getMember().getId().equals(memberId)).findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Học viên chưa được giao kế hoạch này"));
    }

    private List<User> addCurrentMembers(TrainingPlan p) {
        Set<Integer> existing = p.getRecipients().stream().map(r -> r.getMember().getId()).collect(Collectors.toSet());
        List<User> added = new ArrayList<>();
        for (User member : bookings.findTrainingPlanMembers(p.getClassEntity().getId())) {
            if (!existing.add(member.getId())) continue;
            TrainingPlanRecipient recipient = new TrainingPlanRecipient();
            recipient.setPlan(p); recipient.setMember(member); p.getRecipients().add(recipient);
            added.add(member);
        }
        return added;
    }

    private List<User> notificationRecipients(TrainingPlan plan) {
        return isClass(plan)
                ? plan.getRecipients().stream().map(TrainingPlanRecipient::getMember).toList()
                : List.of(plan.getMember());
    }

    private boolean hasResults(TrainingPlan p, TrainingExercise e) {
        return e.isCompleted() || !trim(e.getMemberResult()).isEmpty() || (isClass(p) && p.getRecipients().stream()
                .flatMap(r -> r.getResults().stream()).anyMatch(r -> r.getExercise() == e || Objects.equals(r.getExercise().getId(), e.getId())));
    }

    private List<ExerciseView> exerciseViews(TrainingPlan p, TrainingPlanRecipient recipient) {
        Map<Integer, TrainingExerciseResult> results = recipient == null ? Map.of() : recipient.getResults().stream()
                .collect(Collectors.toMap(r -> r.getExercise().getId(), Function.identity()));
        return p.getExercises().stream().sorted(Comparator.comparingInt(TrainingExercise::getPosition)).map(e -> {
            TrainingExerciseResult r = results.get(e.getId());
            return new ExerciseView(e.getId(), e.getTrainingDate(), e.getName(), e.getInstructions(), e.getTarget(),
                    isClass(p) ? r != null && r.isCompleted() : e.isCompleted(),
                    isClass(p) ? r == null ? "" : r.getMemberResult() : e.getMemberResult(),
                    recipient == null ? hasResults(p, e) : r != null);
        }).toList();
    }

    private long completed(TrainingPlanRecipient r) { return r.getResults().stream().filter(TrainingExerciseResult::isCompleted).count(); }

    private PlanView view(TrainingPlan p, boolean detail) { return view(p, detail, null); }

    private PlanView view(TrainingPlan p, boolean detail, Integer viewerMemberId) {
        TrainingPlanRecipient current = isClass(p) && viewerMemberId != null ? recipient(p, viewerMemberId) : null;
        boolean editable = editable(p) && (current == null || enrollmentValid(p, current.getMember()));
        User member = current == null ? p.getMember() : current.getMember();
        boolean canRecord = member != null && canRecord(p, member);
        int total = p.getExercises().size();
        long completed = isClass(p) ? current == null ? p.getRecipients().stream().mapToLong(this::completed).sum() : completed(current)
                : p.getExercises().stream().filter(TrainingExercise::isCompleted).count();
        if (isClass(p) && viewerMemberId == null) total *= p.getRecipients().size();
        int readRevision = current == null ? p.getReadRevision() : current.getReadRevision();
        List<RecipientView> recipients = isClass(p) && detail && viewerMemberId == null ? p.getRecipients().stream().map(r ->
                new RecipientView(r.getMember().getId(), r.getMember().getFullName(), completed(r), p.getExercises().size(),
                        p.getPublishedRevision() > r.getReadRevision(), enrollmentValid(p, r.getMember()), r.getCoachFeedback(), exerciseViews(p, r))).toList() : List.of();
        Set<Integer> existing = p.getRecipients().stream().map(r -> r.getMember().getId()).collect(Collectors.toSet());
        List<MemberOption> candidates = isClass(p) && detail && viewerMemberId == null && editable && "ASSIGNED".equals(p.getStatus()) && !today().isAfter(p.getEndDate())
                ? bookings.findTrainingPlanMembers(p.getClassEntity().getId()).stream().filter(u -> !existing.contains(u.getId()))
                    .map(u -> new MemberOption(u.getId(), u.getFullName())).toList() : List.of();
        return new PlanView(p.getId(), p.getClassEntity().getId(), p.getClassEntity().getName(),
                isClass(p) ? null : p.getMember().getId(), isClass(p) ? "Cả lớp" : p.getMember().getFullName(), p.getClassEntity().getCoach().getFullName(),
                p.getCreatedBy().getFullName(), p.getTitle(), p.getGoal(), p.getAssessment(), p.getStartDate(),
                p.getEndDate(), p.getStatus(), p.getCoachFeedback(), p.getPublishedRevision(),
                p.getPublishedRevision() > readRevision, p.getPublishedAt(), p.getUpdatedAt(), p.getVersion(),
                editable, canRecord, total, completed, detail ? exerciseViews(p, current) : List.of(), p.getScope(),
                isClass(p) ? p.getRecipients().size() : 1, current == null ? null : current.getCoachFeedback(), recipients, candidates);
    }

    private boolean hasRole(User u, String role) { return u.getRole() != null && role.equalsIgnoreCase(u.getRole().getRoleName()); }
    private String trim(String s) { return s == null ? "" : s.trim(); }
    private LocalDate today() { return LocalDate.now(paymentClock); }
    private LocalDateTime now() { return LocalDateTime.now(paymentClock); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
