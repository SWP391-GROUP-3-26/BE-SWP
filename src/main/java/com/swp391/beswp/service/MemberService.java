package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberListResponse;
import com.swp391.beswp.dto.MemberResponse;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.UserRepository;
import com.swp391.beswp.repository.MemberSubscriptionRepository;
import com.swp391.beswp.repository.ClassBookingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final UserRepository userRepository;
    private final MemberSubscriptionRepository subscriptionRepository;
    private final ClassBookingRepository bookingRepository;
    private final Clock paymentClock;

    public MemberListResponse searchMembers(String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0; size must be between 1 and 100");
        }
        var pageable = PageRequest.of(page, size, Sort.by("id"));
        String normalized = keyword == null ? "" : keyword.trim();
        Page<User> members = normalized.isEmpty() ? Page.empty(pageable)
                : userRepository.searchMembers(normalized, pageable);
        return MemberListResponse.success(members);
    }

    public MemberResponse getMemberById(Integer id) {
        User member = userRepository.findByIdAndRoleRoleNameIgnoreCase(id, "Member")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        LocalDateTime now = LocalDateTime.now(paymentClock);
        LocalDate today = now.toLocalDate();
        var packages = subscriptionRepository.findActiveSubscriptionsByUserId(id, today).stream()
                .filter(subscription -> !subscription.getStartDate().isAfter(today))
                .map(MemberResponse.PackageData::fromEntity).toList();

        var classes = new LinkedHashMap<Integer, MemberResponse.ClassData>();
        for (var booking : bookingRepository.findMyBookings(id, "Booked", null)) {
            var session = booking.getSession();
            var course = session.getClassEntity();
            if (!ClassSchedule.isOpen(course) || ClassSchedule.expired(course, today)
                    || !"Scheduled".equalsIgnoreCase(ClassSchedule.sessionStatus(session, today))
                    || !ClassSchedule.withinTerm(course, session.getDate())
                    || !session.getDate().atTime(session.getEndTime()).isAfter(now)) continue;
            classes.computeIfAbsent(course.getId(), ignored -> MemberResponse.ClassData.fromBooking(booking));
        }
        return MemberResponse.success(member, packages, List.copyOf(classes.values()));
    }
}
