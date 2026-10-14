package com.swp391.beswp.service;

import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.ClassSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public final class ClassSchedule {
    private ClassSchedule() { }

    public static List<String> normalizeDays(List<String> input) {
        if (input == null || input.isEmpty()) throw badRequest("Vui lòng chọn ít nhất một ngày học trong tuần");
        SortedSet<DayOfWeek> days = new TreeSet<>();
        for (String raw : input) {
            if (raw == null) throw badRequest("Ngày học trong tuần không hợp lệ");
            String value = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            DayOfWeek day = switch (value) {
                case "t2", "thu2", "thuhai", "monday", "mon" -> DayOfWeek.MONDAY;
                case "t3", "thu3", "thuba", "tuesday", "tue" -> DayOfWeek.TUESDAY;
                case "t4", "thu4", "thutu", "wednesday", "wed" -> DayOfWeek.WEDNESDAY;
                case "t5", "thu5", "thunam", "thursday", "thu" -> DayOfWeek.THURSDAY;
                case "t6", "thu6", "thusau", "friday", "fri" -> DayOfWeek.FRIDAY;
                case "t7", "thu7", "thubay", "saturday", "sat" -> DayOfWeek.SATURDAY;
                case "cn", "chunhat", "sunday", "sun" -> DayOfWeek.SUNDAY;
                default -> throw badRequest("Ngày học trong tuần không hợp lệ: " + raw);
            };
            days.add(day);
        }
        return days.stream().map(ClassSchedule::code).toList();
    }

    public static List<String> normalizeDays(String input) {
        if (input == null || input.isBlank()) return List.of();
        String separator = input.trim().matches("(?i)(T[2-7]|CN)(\\s+(T[2-7]|CN))*") ? "\\s+" : "[,;/\\-]+";
        return normalizeDays(Arrays.asList(input.trim().split(separator, -1)));
    }

    public static String code(DayOfWeek day) {
        return day == DayOfWeek.SUNDAY ? "CN" : "T" + (day.getValue() + 1);
    }

    public static LocalDate endDate(LocalDate start, Integer weeks) {
        if (start == null) throw badRequest("Ngày học đầu tiên không được để trống");
        if (weeks == null || weeks <= 0) throw badRequest("Số tuần học phải là số nguyên dương");
        try {
            LocalDate end = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(weeks - 1L);
            if (start.getYear() < 1 || end.getYear() > 9999) throw badRequest("Thời hạn lớp vượt phạm vi ngày hợp lệ");
            return end;
        } catch (DateTimeException | ArithmeticException ex) {
            throw badRequest("Thời hạn lớp vượt phạm vi ngày hợp lệ");
        }
    }

    public static List<LocalDate> sessionDates(LocalDate start, LocalDate end, List<String> days) {
        if (!days.contains(code(start.getDayOfWeek()))) throw badRequest("Ngày học đầu tiên phải thuộc các thứ đã chọn");
        return start.datesUntil(end.plusDays(1)).filter(d -> days.contains(code(d.getDayOfWeek()))).toList();
    }

    public static boolean expired(ClassEntity c, LocalDate today) {
        return c.getEndDate() != null && today.isAfter(c.getEndDate());
    }

    public static String effectiveStatus(ClassEntity c, LocalDate today) {
        String status = "Complete".equalsIgnoreCase(c.getStatus()) ? "Completed" : c.getStatus();
        return expired(c, today) && isOpen(c) ? "Completed" : status;
    }

    public static boolean isOpen(ClassEntity c) {
        return "Active".equalsIgnoreCase(c.getStatus()) || "Open".equalsIgnoreCase(c.getStatus());
    }

    public static boolean withinTerm(ClassEntity c, LocalDate date) {
        return date != null && (c.getDate() == null || !date.isBefore(c.getDate()))
                && (c.getEndDate() == null || !date.isAfter(c.getEndDate()));
    }

    public static boolean bookable(ClassEntity c, ClassSession session, LocalDateTime now) {
        if (!isOpen(c) || expired(c, now.toLocalDate()) || session == null
                || !"Scheduled".equalsIgnoreCase(session.getStatus()) || session.getStartTime() == null
                || !withinTerm(c, session.getDate()) || !session.getDate().atTime(session.getStartTime()).isAfter(now)) return false;
        return c.getEndDate() == null || normalizeDays(c.getDaysOfWeek()).contains(code(session.getDate().getDayOfWeek()));
    }

    public static String bookingStatus(ClassBooking booking, LocalDate today) {
        if (!expired(booking.getSession().getClassEntity(), today)) return booking.getStatus();
        if ("Booked".equalsIgnoreCase(booking.getStatus())) {
            return withinTerm(booking.getSession().getClassEntity(), booking.getSession().getDate()) ? "Completed" : "Cancelled";
        }
        if ("Pending".equalsIgnoreCase(booking.getStatus())) return "Cancelled";
        return booking.getStatus();
    }

    public static String sessionStatus(ClassSession session, LocalDate today) {
        ClassEntity c = session.getClassEntity();
        if (expired(c, today) && "Scheduled".equalsIgnoreCase(session.getStatus())) {
            return withinTerm(c, session.getDate()) ? "Completed" : "Cancelled";
        }
        return session.getStatus();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
