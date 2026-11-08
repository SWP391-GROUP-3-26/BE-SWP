package com.swp391.beswp.service;

import com.swp391.beswp.dto.ClassFormOptionsResponse;
import com.swp391.beswp.dto.ClassListResponse;
import com.swp391.beswp.dto.ClassResponse;
import com.swp391.beswp.dto.CreateClassRequest;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.ClassSession;
import com.swp391.beswp.entity.Room;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.ClassSessionRepository;
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
import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClassService {

    private static final List<ClassFormOptionsResponse.TimeSlotOption> TIME_SLOTS = List.of(
            new ClassFormOptionsResponse.TimeSlotOption(1, "07:00", "09:15"),
            new ClassFormOptionsResponse.TimeSlotOption(2, "09:30", "11:45"),
            new ClassFormOptionsResponse.TimeSlotOption(3, "12:30", "14:45"),
            new ClassFormOptionsResponse.TimeSlotOption(4, "15:00", "17:15")
    );

    private final ClassRepository classRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ClassSessionRepository classSessionRepository;
    private final Clock paymentClock;

    @Transactional
    public ClassResponse createClass(CreateClassRequest request) {
        String name = normalizeText(request.getName());
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ApiMessages.text("class.name.required"));
        }

        if (classRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ApiMessages.text("class.name.duplicate", name));
        }

        Subject subject = subjectRepository.findByIdForUpdate(request.getSubjectId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ApiMessages.text("class.subject.notFound", request.getSubjectId())
                ));

        boolean gymSubject = isGymOrFitness(subject);
        boolean requestedPt = Boolean.TRUE.equals(request.getHasPt());
        if (requestedPt && !gymSubject) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ApiMessages.text("class.pt.nonGym"));
        }

        // Resource locks serialize concurrent creations that share a coach or room.
        User coach = userRepository.findByIdForUpdate(request.getCoachId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ApiMessages.text("class.coach.notFound", request.getCoachId())
                ));

        if (coach.getRole() == null || !"Coach".equalsIgnoreCase(coach.getRole().getRoleName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ApiMessages.text("class.coach.invalid", coach.getFullName())
            );
        }

        Room room = roomRepository.findByIdForUpdate(request.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ApiMessages.text("class.room.notFound", request.getRoomId())
                ));

        if (!RoomService.usable(room)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ApiMessages.text("class.room.inactive"));
        }
        if (!subject.getAllowedRoomTypes().contains(room.getType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ApiMessages.text("class.room.incompatible"));
        }
        if (request.getMaxCapacity() > room.getMaxCapacity()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ApiMessages.text("class.capacity.room", request.getMaxCapacity(), room.getName(), room.getMaxCapacity())
            );
        }

        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();
        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ApiMessages.text("class.time.required"));
        }
        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ApiMessages.text("class.time.order")
            );
        }

        var slot = TIME_SLOTS.stream()
                .filter(option -> option.id().equals(request.getSlotId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, ApiMessages.text("class.slot.invalid")));
        if (startTime.isBefore(LocalTime.parse(slot.startTime()))
                || endTime.isAfter(LocalTime.parse(slot.endTime()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ApiMessages.text("class.time.slot", slot.id(), slot.startTime(), slot.endTime()));
        }

        List<String> cleanedDays = ClassSchedule.normalizeDays(request.getDaysOfWeek());
        LocalDate endDate = ClassSchedule.endDate(request.getStartDate(), request.getDurationWeeks());
        List<LocalDate> sessionDates = ClassSchedule.sessionDates(request.getStartDate(), endDate, cleanedDays);

        String daysOfWeekStr = String.join(", ", cleanedDays);
        String requestedStatus = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : "Active";
        String status = List.of("Active", "Open", "Closed", "Cancelled", "Completed", "Cancel").stream()
                .filter(s -> s.equalsIgnoreCase(requestedStatus)
                        || (s.equals("Completed") && "Complete".equalsIgnoreCase(requestedStatus)))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, ApiMessages.text("class.status.invalid")));

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
                .durationWeeks(request.getDurationWeeks())
                .endDate(endDate)
                .hasPt(gymSubject && requestedPt)
                .build();
        newClass.setStatus(ClassSchedule.effectiveStatus(newClass, LocalDate.now(paymentClock)));
        if (!List.of("Completed", "Cancelled", "Cancel").contains(newClass.getStatus())) {
            validateScheduleConflicts(room.getId(), coach.getId(), startTime, endTime, sessionDates);
        }

        ClassEntity saved = classRepository.save(newClass);
        classSessionRepository.saveAll(sessionDates.stream().map(date -> ClassSession.builder()
                .classEntity(saved).date(date).startTime(startTime).endTime(endTime)
                .status("Cancel".equalsIgnoreCase(saved.getStatus()) || "Cancelled".equalsIgnoreCase(saved.getStatus())
                        ? "Cancelled" : "Completed".equalsIgnoreCase(saved.getStatus()) ? "Completed" : "Scheduled")
                .build()).toList());
        return ClassResponse.success(ApiMessages.text("class.created"), saved, LocalDate.now(paymentClock));
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
                null
        );
        LocalDate today = LocalDate.now(paymentClock);
        String requestedStatus = "Complete".equalsIgnoreCase(st) ? "Completed" : st;
        classes = classes.stream().filter(c -> !StringUtils.hasText(requestedStatus)
                || requestedStatus.equalsIgnoreCase(ClassSchedule.effectiveStatus(c, today))).toList();
        return ClassListResponse.success(classes, today);
    }

    @Transactional(readOnly = true)
    public ClassResponse getClassById(Integer id) {
        ClassEntity classEntity = classRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ApiMessages.text("class.notFound", id)
                ));
        return ClassResponse.success(ApiMessages.text("class.loaded"), classEntity, LocalDate.now(paymentClock));
    }

    @Transactional(readOnly = true)
    public ClassFormOptionsResponse getFormOptions() {
        List<Subject> subjects = subjectRepository.findAll();
        List<User> coaches = userRepository.findByRole_RoleNameIgnoreCaseAndStatusIgnoreCase("Coach", "Active");
        if (coaches.isEmpty()) {
            coaches = userRepository.findByRole_RoleNameIgnoreCase("Coach");
        }
        List<Room> rooms = roomRepository.findAllByOrderByNameAsc().stream().filter(RoomService::usable).toList();

        List<ClassFormOptionsResponse.SubjectOption> subjectOptions = subjects.stream()
                .map(s -> ClassFormOptionsResponse.SubjectOption.builder()
                        .id(s.getId())
                        .code(String.format("SUB-%03d", s.getId()))
                        .name(s.getName())
                        .category(s.getCategory())
                        .allowedRoomTypes(new java.util.LinkedHashSet<>(s.getAllowedRoomTypes()))
                        .supportsPt(isGymOrFitness(s))
                        .displayText(String.format("%s (%s)", s.getName(), String.format("SUB-%03d", s.getId())))
                        .build())
                .toList();

        List<ClassFormOptionsResponse.CoachOption> coachOptions = coaches.stream()
                .map(c -> ClassFormOptionsResponse.CoachOption.builder()
                        .id(c.getId())
                        .fullName(c.getFullName())
                        .username(c.getUsername())
                        .phone(c.getPhone())
                        .displayText(ApiMessages.text("class.coach.display", c.getFullName()))
                        .build())
                .toList();

        List<ClassFormOptionsResponse.RoomOption> roomOptions = rooms.stream()
                .map(r -> ClassFormOptionsResponse.RoomOption.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .maxCapacity(r.getMaxCapacity())
                        .type(r.getType()).typeLabel(r.getType().getLabel()).location(r.getLocation())
                        .displayText(ApiMessages.text("class.room.display", r.getName(), r.getMaxCapacity()))
                        .build())
                .toList();

        return ClassFormOptionsResponse.builder()
                .success(true)
                .message(ApiMessages.text("class.options.loaded"))
                .data(ClassFormOptionsResponse.OptionData.builder()
                        .subjects(subjectOptions)
                        .coaches(coachOptions)
                        .rooms(roomOptions)
                        .timeSlots(TIME_SLOTS)
                        .build())
                .build();
    }

    @Transactional
    public void deleteClass(Integer id) {
        ClassEntity classEntity = classRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        ApiMessages.text("class.notFound", id)
                ));
        classRepository.delete(classEntity);
    }

    private void validateScheduleConflicts(
            Integer roomId,
            Integer coachId,
            LocalTime newStart,
            LocalTime newEnd,
            List<LocalDate> dates
    ) {
        List<ClassEntity> roomClasses = classRepository.findActiveClassesByRoomId(roomId);
        for (ClassEntity existing : roomClasses) {
            ClassSession overlap = conflictingSession(existing, dates, newStart, newEnd);
            if (overlap != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, ApiMessages.text(
                            "class.conflict.room", existing.getRoom().getName(), existing.getName(), overlap.getDate(),
                            overlap.getStartTime(), overlap.getEndTime()));
            }
        }

        List<ClassEntity> coachClasses = classRepository.findActiveClassesByCoachId(coachId);
        for (ClassEntity existing : coachClasses) {
            ClassSession overlap = conflictingSession(existing, dates, newStart, newEnd);
            if (overlap != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, ApiMessages.text(
                            "class.conflict.coach", existing.getCoach().getFullName(), existing.getName(), overlap.getDate(),
                            overlap.getStartTime(), overlap.getEndTime()));
            }
        }
    }

    private ClassSession conflictingSession(ClassEntity existing, List<LocalDate> dates, LocalTime start, LocalTime end) {
        String status = ClassSchedule.effectiveStatus(existing, LocalDate.now(paymentClock));
        if (ClassSchedule.expired(existing, LocalDate.now(paymentClock)) || "Completed".equalsIgnoreCase(status)
                || "Cancelled".equalsIgnoreCase(status) || "Cancel".equalsIgnoreCase(status)) return null;
        return classSessionRepository.findActiveSessionsByClassId(existing.getId()).stream()
                .filter(s -> "Scheduled".equalsIgnoreCase(s.getStatus()) && dates.contains(s.getDate())
                        && ClassSchedule.withinTerm(existing, s.getDate())
                        && isTimeOverlap(start, end, s.getStartTime(), s.getEndTime()))
                .findFirst().orElse(null);
    }

    private boolean isTimeOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) {
            return false;
        }
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private String normalizeText(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isGymOrFitness(Subject subject) {
        return isGymOrFitnessValue(subject == null ? null : subject.getName())
                || isGymOrFitnessValue(subject == null ? null : subject.getCategory());
    }

    private boolean isGymOrFitnessValue(String value) {
        if (!StringUtils.hasText(value)) return false;
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return List.of("gym", "fitness", "fitness gym", "gym fitness").contains(normalized);
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
