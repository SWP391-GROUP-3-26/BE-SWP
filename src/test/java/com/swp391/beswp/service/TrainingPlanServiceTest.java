package com.swp391.beswp.service;

import com.swp391.beswp.dto.TrainingPlanDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrainingPlanServiceTest {
    TrainingPlanRepository plans = mock(TrainingPlanRepository.class);
    ClassRepository classes = mock(ClassRepository.class);
    ClassBookingRepository bookings = mock(ClassBookingRepository.class);
    UserRepository users = mock(UserRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    MemberNotificationService notifications = mock(MemberNotificationService.class);
    TrainingPlanService service = new TrainingPlanService(plans, classes, bookings, users, clock, notifications);
    User coach, member;
    ClassEntity classroom;
    TrainingPlan saved;
    final LocalDate today = LocalDate.of(2026, 10, 30);

    @BeforeEach void setup() {
        coach = user(1, "Coach"); member = user(2, "Member");
        classroom = ClassEntity.builder().id(10).name("Bơi cơ bản").coach(coach).status("Active")
                .date(today.minusDays(7)).endDate(today.plusDays(21)).build();
        when(users.findById(1)).thenReturn(Optional.of(coach));
        when(users.findById(2)).thenReturn(Optional.of(member));
        when(classes.findByIdForUpdate(10)).thenReturn(Optional.of(classroom));
        when(classes.findById(10)).thenReturn(Optional.of(classroom));
        when(plans.findClassId(30)).thenReturn(Optional.of(10));
        when(plans.findByIdForUpdate(30)).thenAnswer(call -> Optional.ofNullable(saved));
        when(bookings.isTrainingPlanMember(10, 2)).thenReturn(true);
        when(plans.saveAndFlush(any())).thenAnswer(call -> {
            saved = call.getArgument(0); saved.setId(30);
            int id = 0;
            for (TrainingExercise exercise : saved.getExercises()) exercise.setId(++id);
            when(plans.findById(30)).thenReturn(Optional.of(saved));
            return saved;
        });
    }

    User user(int id, String roleName) {
        Role role = new Role(); role.setRoleName(roleName);
        User user = new User(); user.setId(id); user.setRole(role); user.setStatus("Active"); user.setFullName(roleName + id);
        return user;
    }
    Authentication auth(int id, String role) {
        return new UsernamePasswordAuthenticationToken(String.valueOf(id), "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    ExerciseInput exercise(Integer id, LocalDate date) { return new ExerciseInput(id, date, "Bơi", "Giữ nhịp thở", "200m"); }
    SavePlan input(boolean publish, List<ExerciseInput> exercises) {
        return new SavePlan(10, 2, "Tuần 1", "Cải thiện kỹ thuật", "Cơ bản", today, today.plusDays(7), publish, exercises, 0L);
    }
    SavePlan input(boolean publish) { return input(publish, List.of(exercise(null, today))); }
    void status(HttpStatus expected, Runnable operation) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, operation::run).getStatusCode());
    }

    @Test void draftIsPrivateAndDoesNotNotifyMember() {
        PlanView draft = service.create(auth(1, "Coach"), input(false));
        assertEquals("DRAFT", draft.status()); assertFalse(draft.unread()); assertEquals(0, draft.publishedRevision());
        status(HttpStatus.NOT_FOUND, () -> service.detail(auth(2, "Member"), 30, false));
    }

    @Test void publishReadAndUpdatePersistNotificationRevisions() {
        PlanView published = service.create(auth(1, "Coach"), input(true));
        assertTrue(published.unread()); assertEquals(1, published.publishedRevision());
        assertFalse(service.markRead(auth(2, "Member"), 30, 1).unread());
        service.update(auth(1, "Coach"), 30, input(true, List.of(exercise(1, today))));
        assertEquals(2, saved.getPublishedRevision());
        assertTrue(service.markRead(auth(2, "Member"), 30, 1).unread(), "Reading old revision must not acknowledge a concurrent update");
        assertFalse(service.markRead(auth(2, "Member"), 30, 2).unread());
        status(HttpStatus.BAD_REQUEST, () -> service.markRead(auth(2, "Member"), 30, 3));
    }

    @Test void coachCannotAssignOtherCoachsClass() {
        classroom.setCoach(user(9, "Coach"));
        status(HttpStatus.FORBIDDEN, () -> service.create(auth(1, "Coach"), input(true)));
        verify(plans, never()).saveAndFlush(any());
    }

    @Test void inactiveAccountsAndWrongRoleCannotWrite() {
        coach.setStatus("Locked");
        status(HttpStatus.FORBIDDEN, () -> service.create(auth(1, "Coach"), input(true)));
        status(HttpStatus.FORBIDDEN, () -> service.create(auth(2, "Member"), input(true)));
        status(HttpStatus.UNAUTHORIZED, () -> service.create(null, input(true)));
    }

    @Test void pendingOrCancelledEnrollmentCannotReceivePlans() {
        when(bookings.isTrainingPlanMember(10, 2)).thenReturn(false);
        status(HttpStatus.CONFLICT, () -> service.create(auth(1, "Coach"), input(true)));
    }

    @Test void classExpiryIsEnforcedWithoutWaitingForScheduledCompletion() {
        classroom.setEndDate(today.minusDays(1));
        status(HttpStatus.CONFLICT, () -> service.create(auth(1, "Coach"), input(true)));
        classroom.setEndDate(today.plusDays(7)); classroom.setStatus("Cancelled");
        status(HttpStatus.CONFLICT, () -> service.create(auth(1, "Coach"), input(false)));
    }

    @Test void acceptsExercisesWithoutDatesAndValidatesPlanTerm() {
        var plan = service.create(auth(1, "Coach"), input(true, List.of(exercise(null, null))));
        assertTrue(plan.canRecord());
        assertEquals(today, saved.getExercises().getFirst().getTrainingDate());
        classroom.setEndDate(today.plusDays(3));
        status(HttpStatus.BAD_REQUEST, () -> service.create(auth(1, "Coach"), input(true)));
    }

    @Test void preventsDuplicateAssignments() {
        when(plans.existsByClassEntity_IdAndMember_IdAndStatusAndIdNot(10, 2, "ASSIGNED", -1)).thenReturn(true);
        status(HttpStatus.CONFLICT, () -> service.create(auth(1, "Coach"), input(true)));
    }

    @Test void membersCannotReadOtherMembersPlans() {
        service.create(auth(1, "Coach"), input(true));
        when(users.findById(3)).thenReturn(Optional.of(user(3, "Member")));
        status(HttpStatus.NOT_FOUND, () -> service.detail(auth(3, "Member"), 30, false));
        status(HttpStatus.NOT_FOUND, () -> service.markRead(auth(3, "Member"), 30, 1));
        status(HttpStatus.NOT_FOUND, () -> service.recordResult(auth(3, "Member"), 30, 1, new ExerciseResult(true, "", 0L)));
    }

    @Test void preservesResultsWhenCoachEditsAndRejectsRemovingRecordedExercises() {
        service.create(auth(1, "Coach"), input(true));
        service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "Đã bơi 200m", 0L));
        PlanView edited = service.update(auth(1, "Coach"), 30, input(true, List.of(exercise(1, today))));
        assertEquals("Đã bơi 200m", edited.exercises().getFirst().memberResult());
        assertEquals(1, edited.completedExercises());
        status(HttpStatus.CONFLICT, () -> service.update(auth(1, "Coach"), 30, input(true)));
    }

    @Test void rejectsUnknownAndRepeatedExerciseIds() {
        service.create(auth(1, "Coach"), input(false));
        status(HttpStatus.BAD_REQUEST, () -> service.update(auth(1, "Coach"), 30, input(false, List.of(exercise(99, today)))));
        status(HttpStatus.BAD_REQUEST, () -> service.update(auth(1, "Coach"), 30, input(false, List.of(exercise(1, today), exercise(1, today)))));
    }

    @Test void recordsWithinPlanPeriodRegardlessOfLegacyExerciseDate() {
        service.create(auth(1, "Coach"), input(true, List.of(exercise(null, null))));
        saved.getExercises().getFirst().setTrainingDate(today.plusDays(1));
        assertEquals(1, service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "Đã tập", 0L)).completedExercises());
        saved.setStartDate(today.plusDays(1));
        status(HttpStatus.CONFLICT, () -> service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "", 0L)));
        saved.setStartDate(today); saved.setEndDate(today.minusDays(1));
        status(HttpStatus.CONFLICT, () -> service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "", 0L)));
        saved.setEndDate(today.plusDays(7));
        when(bookings.isTrainingPlanMember(10, 2)).thenReturn(false);
        status(HttpStatus.CONFLICT, () -> service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "", 0L)));
        assertNotNull(service.detail(auth(2, "Member"), 30, false), "Past plans remain visible after unenrollment");
    }

    @Test void completedPlanKeepsHistoryAndStopsMemberWrites() {
        service.create(auth(1, "Coach"), input(true));
        service.review(auth(1, "Coach"), 30, new ReviewPlan("COMPLETED", "Tiến bộ tốt", 0L));
        PlanView result = service.detail(auth(2, "Member"), 30, false);
        assertEquals("Tiến bộ tốt", result.coachFeedback()); assertFalse(result.canRecord()); assertFalse(result.editable());
        status(HttpStatus.CONFLICT, () -> service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "", 0L)));
    }

    @Test void reassignedCoachCanTakeOverButCreatorIsRetained() {
        service.create(auth(1, "Coach"), input(true));
        User replacement = user(4, "Coach");
        when(users.findById(4)).thenReturn(Optional.of(replacement)); classroom.setCoach(replacement);
        status(HttpStatus.NOT_FOUND, () -> service.detail(auth(1, "Coach"), 30, true));
        PlanView view = service.detail(auth(4, "Coach"), 30, true);
        assertEquals("Coach4", view.coachName()); assertEquals("Coach1", view.createdByName());
    }

    @Test void staleVersionCannotOverwriteNewerResults() {
        service.create(auth(1, "Coach"), input(true)); saved.setVersion(1);
        status(HttpStatus.CONFLICT, () -> service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "", 0L)));
        status(HttpStatus.CONFLICT, () -> service.update(auth(1, "Coach"), 30, input(true, List.of(exercise(1, today)))));
    }

    SavePlan classInput(boolean publish, Integer exerciseId) {
        return new SavePlan(10, null, "Cả lớp tuần 1", "Cải thiện kỹ thuật", "Lưu ý chung", today,
                today.plusDays(7), publish, List.of(exercise(exerciseId, null)), 0L, "CLASS");
    }

    User secondMember() {
        User second = user(3, "Member");
        when(users.findById(3)).thenReturn(Optional.of(second));
        when(bookings.isTrainingPlanMember(10, 3)).thenReturn(true);
        return second;
    }

    @Test void classDraftSnapshotsRecipientsOnlyWhenPublished() {
        when(bookings.findTrainingPlanMembers(10)).thenReturn(List.of(member));
        var draft = service.create(auth(1, "Coach"), classInput(false, null));
        assertEquals("CLASS", draft.scope()); assertEquals(0, draft.recipientCount());
        status(HttpStatus.NOT_FOUND, () -> service.detail(auth(2, "Member"), 30, false));
        User second = secondMember();
        when(bookings.findTrainingPlanMembers(10)).thenReturn(List.of(member, second));
        var assigned = service.update(auth(1, "Coach"), 30, classInput(true, 1));
        assertEquals(2, assigned.recipientCount()); assertEquals(2, assigned.totalExercises());
        assertEquals(2, assigned.recipients().size());
    }

    @Test void classResultsReadStateAndFeedbackArePrivateToEachMember() {
        User second = secondMember();
        when(bookings.findTrainingPlanMembers(10)).thenReturn(List.of(member, second));
        service.create(auth(1, "Coach"), classInput(true, null));
        service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "Kết quả riêng", 0L));
        assertFalse(service.markRead(auth(2, "Member"), 30, 1).unread());
        var secondView = service.detail(auth(3, "Member"), 30, false);
        assertTrue(secondView.unread()); assertEquals(0, secondView.completedExercises());
        assertEquals("", secondView.exercises().getFirst().memberResult()); assertTrue(secondView.recipients().isEmpty());
        service.reviewRecipient(auth(1, "Coach"), 30, 2, new RecipientFeedback("Nhận xét riêng", 0L));
        assertEquals("Nhận xét riêng", service.detail(auth(2, "Member"), 30, false).personalFeedback());
        assertNull(service.detail(auth(3, "Member"), 30, false).personalFeedback());
        assertEquals(1, service.detail(auth(1, "Coach"), 30, true).completedExercises());
        assertFalse(saved.getExercises().getFirst().isCompleted(), "Shared content must not hold any student's result");
        service.update(auth(1, "Coach"), 30, classInput(true, 1));
        assertTrue(service.detail(auth(2, "Member"), 30, false).unread());
        assertEquals("Kết quả riêng", service.detail(auth(2, "Member"), 30, false).exercises().getFirst().memberResult());
        status(HttpStatus.CONFLICT, () -> service.update(auth(1, "Coach"), 30, classInput(true, null)));
    }

    @Test void newMembersNeedExplicitAdditionAndExistingResultsSurviveUnenrollment() {
        when(bookings.findTrainingPlanMembers(10)).thenReturn(List.of(member));
        service.create(auth(1, "Coach"), classInput(true, null));
        User second = secondMember();
        when(bookings.findTrainingPlanMembers(10)).thenReturn(List.of(member, second));
        status(HttpStatus.NOT_FOUND, () -> service.detail(auth(3, "Member"), 30, false));
        assertEquals(1, service.detail(auth(1, "Coach"), 30, true).eligibleNewMembers().size());
        service.addRecipients(auth(1, "Coach"), 30, new VersionInput(0L));
        service.addRecipients(auth(1, "Coach"), 30, new VersionInput(0L));
        assertEquals(2, saved.getRecipients().size());
        assertTrue(service.detail(auth(3, "Member"), 30, false).unread());
        service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "Đã tập", 0L));
        when(bookings.isTrainingPlanMember(10, 2)).thenReturn(false);
        assertFalse(service.detail(auth(2, "Member"), 30, false).canRecord());
        assertEquals("Đã tập", service.detail(auth(2, "Member"), 30, false).exercises().getFirst().memberResult());
        status(HttpStatus.CONFLICT, () -> service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(false, "", 0L)));
        assertTrue(service.detail(auth(1, "Coach"), 30, true).editable());
    }

    @Test void classPublicationRejectsEmptyRosterDuplicateAndWrongAudience() {
        status(HttpStatus.CONFLICT, () -> service.create(auth(1, "Coach"), classInput(true, null)));
        when(bookings.findTrainingPlanMembers(10)).thenReturn(List.of(member));
        when(plans.existsByClassEntity_IdAndScopeAndStatusAndIdNot(10, "CLASS", "ASSIGNED", -1)).thenReturn(true);
        status(HttpStatus.CONFLICT, () -> service.create(auth(1, "Coach"), classInput(true, null)));
        assertEquals("INDIVIDUAL", service.create(auth(1, "Coach"), input(true)).scope(), "Personal plan may coexist with class plan");
        var wrong = new SavePlan(10, 2, "Sai", "Sai", "", today, today.plusDays(1), true, List.of(exercise(null, today)), 0L, "CLASS");
        status(HttpStatus.BAD_REQUEST, () -> service.create(auth(1, "Coach"), wrong));
    }

    @Test void classCompletionKeepsResultsAndPreventsFurtherChanges() {
        when(bookings.findTrainingPlanMembers(10)).thenReturn(List.of(member));
        service.create(auth(1, "Coach"), classInput(true, null));
        service.recordResult(auth(2, "Member"), 30, 1, new ExerciseResult(true, "Đã tập", 0L));
        service.review(auth(1, "Coach"), 30, new ReviewPlan("COMPLETED", "Kết thúc", 0L));
        assertFalse(service.detail(auth(2, "Member"), 30, false).canRecord());
        assertEquals(1, service.detail(auth(2, "Member"), 30, false).completedExercises());
        status(HttpStatus.CONFLICT, () -> service.addRecipients(auth(1, "Coach"), 30, new VersionInput(0L)));
        status(HttpStatus.CONFLICT, () -> service.reviewRecipient(auth(1, "Coach"), 30, 2, new RecipientFeedback("", 0L)));
    }

    @Test void expiredClassPlanCannotReceiveNewStudents() {
        when(bookings.findTrainingPlanMembers(10)).thenReturn(List.of(member));
        service.create(auth(1, "Coach"), classInput(true, null));
        saved.setEndDate(today.minusDays(1));
        assertTrue(service.detail(auth(1, "Coach"), 30, true).eligibleNewMembers().isEmpty());
        status(HttpStatus.CONFLICT, () -> service.addRecipients(auth(1, "Coach"), 30, new VersionInput(0L)));
    }
}
