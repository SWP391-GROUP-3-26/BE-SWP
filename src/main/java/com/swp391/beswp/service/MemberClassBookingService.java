package com.swp391.beswp.service;

import com.swp391.beswp.dto.AvailableClassCardResponse;
import com.swp391.beswp.dto.AvailableClassListResponse;
import com.swp391.beswp.dto.ClassConfirmationDetailResponse;
import com.swp391.beswp.dto.MemberClassBookingRequest;
import com.swp391.beswp.dto.MemberClassBookingResponse;
import com.swp391.beswp.dto.MemberSubscriptionInfoResponse;
import com.swp391.beswp.dto.MyClassItemResponse;
import com.swp391.beswp.dto.MyClassListResponse;
import com.swp391.beswp.dto.MemberClassSessionData;
import com.swp391.beswp.entity.ActivityLog;
import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.ClassSession;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ActivityLogRepository;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.ClassSessionRepository;
import com.swp391.beswp.repository.MemberSubscriptionRepository;
import com.swp391.beswp.repository.PaymentRepository;
import com.swp391.beswp.repository.SubjectRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberClassBookingService {

    private final ClassRepository classRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ClassBookingRepository classBookingRepository;
    private final MemberSubscriptionRepository memberSubscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final VnpayService vnpayService;
    private final ClassReservationService reservationService;
    private final java.time.Clock paymentClock;

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,###");

    /**
     * Màn hình 1: "Lớp học của tôi"
     * Lấy danh sách lớp đã đăng ký của hội viên kèm số lượng theo từng trạng thái.
     */
    @Transactional(readOnly = true)
    public MyClassListResponse getMyClasses(String principal, String status, String search) {
        User user = findMemberUser(principal);

        String resolvedStatus = resolveBookingStatusQuery(status);
        String keyword = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;

        List<ClassBooking> allBookings = classBookingRepository.findMyBookings(user.getId(), null, null);
        LocalDate today = LocalDate.now(paymentClock);
        Map<String, List<ClassBooking>> groups = new LinkedHashMap<>();
        for (ClassBooking booking : allBookings) {
            String key = booking.getRegistrationCode() == null ? "legacy:" + booking.getId() : booking.getRegistrationCode();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(booking);
        }

        List<MyClassItemResponse> allItems = groups.values().stream().map(group -> {
            ClassBooking b = group.stream().min(Comparator.comparing(ClassBooking::getId)).orElseThrow();
            ClassSession session = b.getSession();
            ClassEntity classEntity = session.getClassEntity();
            List<Payment> payments = paymentRepository.findAllByBooking_IdOrderByIdDesc(b.getId());
            Payment payment = payments.isEmpty() ? null : payments.getFirst();

            boolean hasDirectPayment = "DIRECT".equals(b.getPaymentMethod()) || (b.getPaymentMethod() == null
                    && paymentRepository.existsByBooking_IdAndStatusIgnoreCase(b.getId(), "Paid"));
            String paymentMethod = hasDirectPayment ? "Thanh toán trực tiếp" : "Trừ gói hội viên";

            String rawStatus = registrationStatus(group, today);
            String displayStatus = mapBookingDisplayStatus(rawStatus);
            boolean canCancel = ("Booked".equals(rawStatus) || "Pending".equals(rawStatus))
                    && group.stream().anyMatch(item -> item.getSession().getDate().atTime(item.getSession().getStartTime())
                    .isAfter(LocalDateTime.now(paymentClock)));
            boolean canRebook = ("Completed".equalsIgnoreCase(rawStatus) || "Cancelled".equalsIgnoreCase(rawStatus))
                    && ClassSchedule.isOpen(classEntity) && !ClassSchedule.expired(classEntity, today);

            String coachName = classEntity.getCoach() != null ? "HLV " + classEntity.getCoach().getFullName() : "Chưa cập nhật";
            String coachAvatar = classEntity.getCoach() != null ? getInitials(classEntity.getCoach().getFullName()) : "HLV";

            String schedule = StringUtils.hasText(classEntity.getDaysOfWeek())
                    ? String.join(", ", ClassSchedule.normalizeDays(classEntity.getDaysOfWeek())) : "Theo lịch";
            String timeSlot = formatTimeRange(session.getStartTime(), session.getEndTime());

            return MyClassItemResponse.builder()
                    .registrationId(b.getId()).sessionCount(group.size())
                    .sessions(group.stream().sorted(Comparator.comparing((ClassBooking item) -> item.getSession().getDate())
                            .thenComparing(item -> item.getSession().getStartTime()))
                            .map(item -> sessionData(item.getSession(), ClassSchedule.bookingStatus(item, today))).toList())
                    .paymentId(payment == null ? null : payment.getId())
                    .paymentStatus(payment == null ? ("PACKAGE".equals(b.getPaymentMethod()) ? "Package" : null) : payment.getStatus())
                    .paymentAmount(payment == null ? null : payment.getAmount())
                    .requiresReview(payment != null && payment.isRequiresReview())
                    .bookingId(b.getId())
                    .classId(classEntity.getId())
                    .classCode(String.format("CLS-%03d", classEntity.getId()))
                    .className(classEntity.getName())
                    .roomName(classEntity.getRoom() != null ? classEntity.getRoom().getName() : "")
                    .schedule(schedule)
                    .timeSlot(timeSlot)
                    .coachName(coachName)
                    .coachAvatar(coachAvatar)
                    .status(displayStatus)
                    .rawStatus(rawStatus)
                    .classStatus(ClassSchedule.effectiveStatus(classEntity, today))
                    .sessionStatus(ClassSchedule.sessionStatus(session, today))
                    .startDate(classEntity.getDate()).durationWeeks(classEntity.getDurationWeeks()).endDate(classEntity.getEndDate())
                    .sessionId(session.getId()).sessionDate(session.getDate())
                    .sessionStartTime(session.getStartTime()).sessionEndTime(session.getEndTime())
                    .canCancel(canCancel)
                    .canRebook(canRebook)
                    .bookingDatetime(b.getBookingDatetime())
                    .paymentMethod(paymentMethod)
                    .build();
        }).toList();

        List<MyClassItemResponse> items = allItems.stream()
                .filter(item -> resolvedStatus == null || resolvedStatus.equalsIgnoreCase(item.getRawStatus()))
                .filter(item -> keyword == null || java.util.stream.Stream.of(item.getClassName(), item.getCoachName(), item.getRoomName())
                        .filter(java.util.Objects::nonNull).anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(keyword))).toList();
        long total = allItems.size();
        long ongoing = allItems.stream().filter(item -> "Booked".equals(item.getRawStatus())).count();
        long completed = allItems.stream().filter(item -> "Completed".equals(item.getRawStatus())).count();
        long cancelled = allItems.stream().filter(item -> "Cancelled".equals(item.getRawStatus())).count();

        return MyClassListResponse.builder()
                .success(true)
                .message("Lấy danh sách lớp học của tôi thành công")
                .counts(MyClassListResponse.Counts.builder()
                        .all(total)
                        .ongoing(ongoing)
                        .completed(completed)
                        .cancelled(cancelled)
                        .build())
                .classes(items)
                .build();
    }

    private String registrationStatus(List<ClassBooking> group, LocalDate today) {
        List<String> statuses = group.stream().map(b -> "Pending".equals(b.getStatus()) && b.getPaymentExpiresAt() != null
                && !b.getPaymentExpiresAt().isAfter(LocalDateTime.now(paymentClock)) ? "Cancelled"
                : ClassSchedule.bookingStatus(b, today)).toList();
        if (statuses.contains("Booked")) return "Booked";
        if (statuses.contains("Pending")) return "Pending";
        return statuses.contains("Cancelled") ? "Cancelled" : "Completed";
    }

    private MemberClassSessionData sessionData(ClassSession session, String bookingStatus) {
        return new MemberClassSessionData(session.getId(), session.getDate(), session.getStartTime(), session.getEndTime(),
                ClassSchedule.sessionStatus(session, LocalDate.now(paymentClock)), bookingStatus);
    }

    /**
     * Màn hình 2: "Đăng ký lớp"
     * Hiển thị thông tin gói hội viên khả dụng và danh sách các lớp học hiện có để học viên tìm kiếm và chọn.
     */
    @Transactional(readOnly = true)
    public AvailableClassListResponse getAvailableClasses(String principal, String search, String category) {
        User user = findMemberUser(principal);
        MemberSubscriptionInfoResponse activeSub = getActiveSubscriptionInfo(user);

        List<ClassEntity> allClasses = classRepository.searchClasses(null, null, null);

        String keyword = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        String filterCat = StringUtils.hasText(category) && !"tất cả".equalsIgnoreCase(category.trim())
                ? category.trim().toLowerCase(Locale.ROOT) : null;

        List<AvailableClassCardResponse> cardList = new ArrayList<>();

        for (ClassEntity c : allClasses) {
            String cStatus = c.getStatus() != null ? c.getStatus().trim() : "";
            if ((!"Active".equalsIgnoreCase(cStatus) && !"Open".equalsIgnoreCase(cStatus))
                    || ClassSchedule.expired(c, LocalDate.now(paymentClock))) {
                continue;
            }

            String subjectName = c.getSubject() != null ? c.getSubject().getName() : "";
            String subjectCategory = c.getSubject() != null && StringUtils.hasText(c.getSubject().getCategory())
                    ? c.getSubject().getCategory() : subjectName;
            String coachName = c.getCoach() != null ? c.getCoach().getFullName() : "";
            String roomName = c.getRoom() != null ? c.getRoom().getName() : "";
            String className = c.getName() != null ? c.getName() : "";

            if (filterCat != null) {
                boolean matchCat = subjectName.toLowerCase(Locale.ROOT).contains(filterCat)
                        || subjectCategory.toLowerCase(Locale.ROOT).contains(filterCat);
                if (!matchCat) {
                    continue;
                }
            }

            if (keyword != null) {
                boolean matchKw = className.toLowerCase(Locale.ROOT).contains(keyword)
                        || coachName.toLowerCase(Locale.ROOT).contains(keyword)
                        || roomName.toLowerCase(Locale.ROOT).contains(keyword)
                        || subjectName.toLowerCase(Locale.ROOT).contains(keyword);
                if (!matchKw) {
                    continue;
                }
            }

            LocalDateTime now = LocalDateTime.now(paymentClock);
            List<ClassSession> upcoming = classSessionRepository.findUpcomingScheduledSessions(c.getId(), now.toLocalDate()).stream()
                    .filter(s -> ClassSchedule.bookable(c, s, now)).toList();
            long enrolledCount = upcoming.stream().mapToLong(s -> classBookingRepository.countReservedMembersBySessionId(s.getId(), now))
                    .max().orElse(0L);
            int remainingSlots = upcoming.isEmpty() ? 0 : Math.max(0, c.getMaxCapacity() - (int) enrolledCount);
            boolean isEnrolled = classBookingRepository.existsActiveBookingByUserIdAndClassId(user.getId(), c.getId(), LocalDateTime.now(paymentClock));
            ClassSession nextSession = nextBookableSession(c, LocalDateTime.now(paymentClock)).orElse(null);
            boolean canRegister = remainingSlots > 0 && !isEnrolled && nextSession != null;

            String capacityUnit = resolveCapacityUnit(subjectName, subjectCategory);
            String remainingSlotsText = String.format("Còn %d %s", remainingSlots, capacityUnit);

            BigDecimal price = c.getPrice() != null ? c.getPrice() : BigDecimal.ZERO;
            String priceFormatted = formatCurrency(price) + " đ/buổi";

            String schedule = formatScheduleWithDays(c.getStartTime(), c.getEndTime(), c.getDaysOfWeek());

            cardList.add(AvailableClassCardResponse.builder()
                    .classId(c.getId())
                    .classCode(String.format("CLS-%03d", c.getId()))
                    .name(c.getName())
                    .subjectId(c.getSubject() != null ? c.getSubject().getId() : null)
                    .subjectName(subjectName)
                    .category(subjectCategory)
                    .description(c.getSubject() != null ? c.getSubject().getDescription() : "")
                    .schedule(schedule)
                    .daysOfWeek(String.join(", ", ClassSchedule.normalizeDays(c.getDaysOfWeek())))
                    .startDate(c.getDate()).durationWeeks(c.getDurationWeeks()).endDate(c.getEndDate())
                    .sessionId(nextSession != null ? nextSession.getId() : null)
                    .sessionDate(nextSession != null ? nextSession.getDate() : null)
                    .sessionStartTime(nextSession != null ? nextSession.getStartTime() : null)
                    .sessionEndTime(nextSession != null ? nextSession.getEndTime() : null)
                    .startTime(c.getStartTime())
                    .endTime(c.getEndTime())
                    .roomName(roomName)
                    .coachName("HLV: " + coachName)
                    .coachAvatar(c.getCoach() != null ? c.getCoach().getAvatarUrl() : null)
                    .maxCapacity(c.getMaxCapacity())
                    .enrolledCount((int) enrolledCount)
                    .remainingSlots(remainingSlots)
                    .capacityUnit(capacityUnit)
                    .remainingSlotsText(remainingSlotsText)
                    .price(price)
                    .priceFormatted(priceFormatted)
                    .packageNote("Hoặc trừ 1 buổi gói")
                    .enrolled(isEnrolled)
                    .canRegister(canRegister)
                    .build());
        }

        Set<String> categories = new LinkedHashSet<>();
        categories.add("Tất cả");
        for (Subject s : subjectRepository.findAll()) {
            if (StringUtils.hasText(s.getName())) {
                categories.add(s.getName().trim());
            }
            if (StringUtils.hasText(s.getCategory())) {
                categories.add(s.getCategory().trim());
            }
        }

        return AvailableClassListResponse.builder()
                .success(true)
                .message("Lấy danh sách lớp học đăng ký thành công")
                .activeSubscription(activeSub)
                .categories(new ArrayList<>(categories))
                .totalAvailableClasses(cardList.size())
                .classes(cardList)
                .build();
    }

    /**
     * Màn hình 3: Pop-up "Xác nhận thông tin đặt lớp"
     * Trả về thông tin chi tiết lớp cùng 2 phương thức thanh toán để học viên lựa chọn.
     */
    @Transactional(readOnly = true)
    public ClassConfirmationDetailResponse getClassConfirmationDetail(String principal, Integer classId) {
        User user = findMemberUser(principal);
        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học với mã: " + classId));

        List<ClassSession> courseSessions = remainingSessions(classEntity, LocalDateTime.now(paymentClock));
        ClassSession nextSession = courseSessions.getFirst();

        MemberSubscriptionInfoResponse subInfo = getActiveSubscriptionInfo(user);

        BigDecimal price = classEntity.getPrice() != null ? classEntity.getPrice() : BigDecimal.ZERO;
        String priceFormatted = formatCurrency(price) + " đ";

        String schedule = formatScheduleWithDays(classEntity.getStartTime(), classEntity.getEndTime(), classEntity.getDaysOfWeek());
        String coachName = classEntity.getCoach() != null ? classEntity.getCoach().getFullName() : "Chưa cập nhật";
        String roomName = classEntity.getRoom() != null ? classEntity.getRoom().getName() : "Chưa xác định";

        String description = (classEntity.getSubject() != null && StringUtils.hasText(classEntity.getSubject().getDescription()))
                ? classEntity.getSubject().getDescription()
                : "Chuỗi bài tập bài bản được hướng dẫn trực tiếp bởi Huấn luyện viên, giúp nâng cao thể lực và sự dẻo dai.";

        boolean eligiblePackage = subInfo.isHasActivePackage() && subInfo.getRemainingSessions() != null
                && subInfo.getRemainingSessions() >= courseSessions.size() && subInfo.getEndDate() != null
                && !courseSessions.getLast().getDate().isAfter(subInfo.getEndDate());
        String packageSubtitle = eligiblePackage
                ? "Khả dụng: " + subInfo.getRemainingSessions() + " buổi"
                : (subInfo.isHasActivePackage() ? "Không khả dụng (Đã hết buổi)" : "Chưa có gói hội viên khả dụng");

        ClassConfirmationDetailResponse.PackagePaymentOption packageOption = ClassConfirmationDetailResponse.PackagePaymentOption.builder()
                .eligible(eligiblePackage)
                .title("Trừ gói hội viên")
                .subtitle(eligiblePackage ? packageSubtitle : "Cần đủ buổi và gói còn hiệu lực đến buổi cuối")
                .remainingSessions(subInfo.getRemainingSessions())
                .requiredSessions(courseSessions.size())
                .packageName(subInfo.getPackageName())
                .note("Đăng ký cần " + courseSessions.size() + " buổi gói và gói phải còn hiệu lực đến buổi cuối")
                .build();

        ClassConfirmationDetailResponse.DirectPaymentOption directOption = ClassConfirmationDetailResponse.DirectPaymentOption.builder()
                .available(true)
                .title("Thanh toán trực tiếp")
                .subtitle("Thẻ / QR / Chuyển khoản")
                .amount(price.multiply(BigDecimal.valueOf(courseSessions.size())))
                .amountFormatted(formatCurrency(price.multiply(BigDecimal.valueOf(courseSessions.size()))) + " đ")
                .build();

        return ClassConfirmationDetailResponse.builder()
                .sessionCount(courseSessions.size()).totalAmount(price.multiply(BigDecimal.valueOf(courseSessions.size())))
                .sessions(courseSessions.stream().map(s -> sessionData(s, null)).toList())
                .success(true)
                .message("Lấy thông tin xác nhận đặt lớp thành công")
                .classId(classEntity.getId())
                .classCode(String.format("CLS-%03d", classEntity.getId()))
                .className(classEntity.getName())
                .startDate(classEntity.getDate()).durationWeeks(classEntity.getDurationWeeks()).endDate(classEntity.getEndDate())
                .sessionId(nextSession.getId()).sessionDate(nextSession.getDate())
                .sessionStartTime(nextSession.getStartTime()).sessionEndTime(nextSession.getEndTime())
                .schedule(schedule)
                .roomName(roomName)
                .coachName(coachName)
                .price(price)
                .priceFormatted(priceFormatted)
                .description(description)
                .packageOption(packageOption)
                .directPaymentOption(directOption)
                .build();
    }

    /**
     * Thực hiện Đăng ký lớp với 1 trong 2 hình thức thanh toán:
     * - Trừ gói hội viên (PACKAGE)
     * - Thanh toán trực tiếp (DIRECT)
     */
    @Transactional
    public MemberClassBookingResponse registerClass(String principal, Integer targetClassId, MemberClassBookingRequest request, String ipAddress) {
        User user = findMemberUser(principal);
        // Serialize package quota checks across different classes for the same member.
        userRepository.findByIdForUpdate(user.getId()).orElseThrow();

        Integer classId = targetClassId != null ? targetClassId : request.getClassId();
        if (classId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã lớp học (classId) không được để trống");
        }

        ClassEntity classEntity = classRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học với mã: " + classId));
        LocalDateTime now = LocalDateTime.now(paymentClock).withNano(0);

        String status = classEntity.getStatus() != null ? classEntity.getStatus().trim() : "";
        if ((!"Active".equalsIgnoreCase(status) && !"Open".equalsIgnoreCase(status))
                || ClassSchedule.expired(classEntity, now.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp học hiện tại không mở đăng ký (Trạng thái: " + status + ")");
        }

        reservationService.expirePending(classId, now);
        if (classBookingRepository.existsActiveBookingByUserIdAndClassId(user.getId(), classEntity.getId(), now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã đăng ký lớp học này rồi. Vui lòng kiểm tra mục 'Lớp học của tôi'");
        }

        List<ClassSession> courseSessions = remainingSessions(classEntity, now);
        for (ClassSession selected : courseSessions) {
            if (classBookingRepository.countReservedMembersBySessionId(selected.getId(), now) >= classEntity.getMaxCapacity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buổi học ngày " + selected.getDate() + " đã hết chỗ");
            }
        }

        String rawMethod = request.getPaymentMethod();
        if (!StringUtils.hasText(rawMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn hình thức thanh toán");
        }

        boolean isPackage = isPackagePayment(rawMethod);
        boolean isDirect = isDirectPayment(rawMethod);

        if (!isPackage && !isDirect) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hình thức thanh toán không hợp lệ. Vui lòng chọn 'Trừ gói hội viên' hoặc 'Thanh toán trực tiếp'");
        }

        String channel = isDirect ? normalizePaymentChannel(request.getPaymentChannel()) : null;
        boolean isCard = isDirect && "Card".equals(channel);
        BigDecimal unitPrice = classEntity.getPrice() == null ? BigDecimal.ZERO : classEntity.getPrice();
        BigDecimal paymentAmount = isDirect ? unitPrice.multiply(BigDecimal.valueOf(courseSessions.size())) : null;
        if (paymentAmount != null && (paymentAmount.signum() < 0 || paymentAmount.precision() - paymentAmount.scale() > 10)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng học phí không hợp lệ hoặc vượt giới hạn thanh toán");
        }
        if (isCard) {
            vnpayService.requireConfigured();
            if (paymentAmount == null || paymentAmount.signum() <= 0
                    || paymentAmount.stripTrailingZeros().scale() > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá lớp phải là số VND nguyên dương để thanh toán VNPay");
            }
        }

        MemberSubscription activeSub = null;
        Integer remainingSessionsAfter = null;

        if (isPackage) {
            Optional<MemberSubscription> subOpt = memberSubscriptionRepository.findCurrentActiveSubscription(user.getId(), LocalDate.now(paymentClock));
            if (subOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn chưa có gói hội viên nào đang hoạt động hoặc gói đã hết hạn. Vui lòng chọn 'Thanh toán trực tiếp' hoặc mua thêm gói dịch vụ");
            }
            activeSub = subOpt.get();
            int total = activeSub.getMembershipPackage() != null ? activeSub.getMembershipPackage().getIncludedClasses() : 0;
            long used = classBookingRepository.countUsedPackageSessions(
                    user.getId(),
                    activeSub.getStartDate().atStartOfDay(),
                    activeSub.getEndDate().atTime(23, 59, 59)
            );
            int remaining = Math.max(0, total - (int) used);
            if (remaining < courseSessions.size() || courseSessions.getLast().getDate().isAfter(activeSub.getEndDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đăng ký cần " + courseSessions.size()
                        + " buổi, gói còn " + remaining + " buổi và phải có hiệu lực đến buổi cuối. Vui lòng chọn gói phù hợp hoặc thanh toán trực tiếp");
            }
            remainingSessionsAfter = remaining - courseSessions.size();
        }

        ClassSession session = courseSessions.getFirst();
        if (!ClassSchedule.bookable(classEntity, session, now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buổi học không còn mở đăng ký");
        }
        LocalDateTime expiresAt = isCard ? now.plusMinutes(VnpayService.PAYMENT_TIMEOUT_MINUTES) : null;
        if (isCard && expiresAt.isAfter(session.getDate().atTime(session.getStartTime()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buổi học phải bắt đầu sau ít nhất 15 phút để thanh toán VNPay");
        }
        String registrationCode = UUID.randomUUID().toString();
        List<ClassBooking> registration = new ArrayList<>();
        for (ClassSession selected : courseSessions) {
            registration.add(classBookingRepository.save(ClassBooking.builder().user(user).session(selected)
                    .registrationCode(registrationCode).bookingDatetime(now).status(isCard ? "Pending" : "Booked")
                    .paymentMethod(isPackage ? "PACKAGE" : "DIRECT").paymentExpiresAt(expiresAt).build()));
        }
        ClassBooking savedBooking = registration.getFirst();

        Payment savedPayment = null;
        String paymentUrl = null;
        if (isDirect) {
            Payment payment = Payment.builder()
                    .booking(savedBooking)
                    .paymentType("Class_Booking")
                    .amount(paymentAmount)
                    .method(channel)
                    .paymentDate(now)
                    .expiresAt(expiresAt)
                    .status(isCard ? "Pending" : "Paid")
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            savedPayment = paymentRepository.save(payment);
            if (isCard) paymentUrl = vnpayService.createPaymentUrl(savedPayment, ipAddress);
        }

        logActivity(
                user,
                "BOOK_CLASS",
                "Class_Booking",
                String.format("Hội viên %s đặt lớp '%s' qua hình thức %s",
                        user.getFullName(),
                        classEntity.getName(),
                        isPackage ? "Trừ gói hội viên" : "Thanh toán trực tiếp")
        );

        String paymentMethodDisplay = isPackage ? "Trừ gói hội viên" : "Thanh toán trực tiếp";
        String schedule = formatScheduleWithDays(classEntity.getStartTime(), classEntity.getEndTime(), classEntity.getDaysOfWeek());

        return MemberClassBookingResponse.builder()
                .registrationId(savedBooking.getId()).sessionCount(courseSessions.size())
                .sessions(courseSessions.stream().map(s -> sessionData(s, isCard ? "Pending" : "Booked")).toList())
                .success(true)
                .message(isCard ? "Đã tạo yêu cầu thanh toán VNPay" : "Đăng ký lớp học thành công! Chúc bạn có buổi tập vui vẻ và hiệu quả.")
                .bookingId(savedBooking.getId())
                .startDate(classEntity.getDate()).durationWeeks(classEntity.getDurationWeeks()).endDate(classEntity.getEndDate())
                .sessionId(session.getId()).sessionDate(session.getDate())
                .sessionStartTime(session.getStartTime()).sessionEndTime(session.getEndTime())
                .classId(classEntity.getId())
                .classCode(String.format("CLS-%03d", classEntity.getId()))
                .className(classEntity.getName())
                .schedule(schedule)
                .roomName(classEntity.getRoom() != null ? classEntity.getRoom().getName() : "")
                .coachName(classEntity.getCoach() != null ? classEntity.getCoach().getFullName() : "")
                .paymentMethod(paymentMethodDisplay)
                .remainingPackageSessions(remainingSessionsAfter)
                .paymentAmount(paymentAmount)
                .bookingStatus(isCard ? "Chờ thanh toán" : "Đang diễn ra")
                .paymentId(savedPayment != null ? savedPayment.getId() : null)
                .paymentStatus(savedPayment != null ? savedPayment.getStatus() : null)
                .paymentUrl(paymentUrl)
                .expiresAt(expiresAt != null ? expiresAt.atOffset(VnpayService.OFFSET) : null)
                .bookingDatetime(savedBooking.getBookingDatetime())
                .build();
    }

    /**
     * Hủy đăng ký lớp học (Thao tác ở màn hình Lớp học của tôi).
     */
    @Transactional
    public void cancelClassBooking(String principal, Integer bookingId) {
        User user = findMemberUser(principal);

        Integer classId = classBookingRepository.findOwnedClassId(bookingId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đăng ký lớp"));
        classRepository.findByIdForUpdate(classId).orElseThrow();

        ClassBooking booking = classBookingRepository.findByIdAndUser_Id(bookingId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin đăng ký lớp học này"));

        if (ClassSchedule.expired(booking.getSession().getClassEntity(), LocalDate.now(paymentClock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp học đã kết thúc");
        }

        if ("Cancelled".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp học này đã được hủy trước đó");
        }

        if ("Completed".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buổi học đã hoàn thành, không thể hủy");
        }

        List<ClassBooking> registration = reservationService.registrationBookings(booking);
        ClassBooking root = registration.getFirst();
        LocalDateTime now = LocalDateTime.now(paymentClock);
        if (registration.stream().noneMatch(b -> b.getSession().getDate().atTime(b.getSession().getStartTime()).isAfter(now))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không còn buổi tương lai có thể hủy");
        }
        for (ClassBooking item : registration) {
            if ("Booked".equals(item.getStatus()) || "Pending".equals(item.getStatus())) {
                item.setStatus(item.getSession().getDate().atTime(item.getSession().getStartTime()).isAfter(now)
                        ? "Cancelled" : "Completed");
            }
        }

        for (Payment payment : paymentRepository.findAllByBooking_IdOrderByIdDesc(root.getId())) {
            if ("Pending".equals(payment.getStatus())) payment.setStatus("Failed");
            if ("Paid".equals(payment.getStatus())) payment.setRequiresReview(true);
        }

        String className = booking.getSession() != null && booking.getSession().getClassEntity() != null
                ? booking.getSession().getClassEntity().getName() : "lớp học";

        logActivity(
                user,
                "CANCEL_BOOKING",
                "Class_Booking",
                String.format("Hội viên %s hủy đặt lớp '%s'", user.getFullName(), className)
        );
    }

    /**
     * Lấy thông tin trạng thái gói hội viên đang hoạt động của học viên.
     */
    @Transactional(readOnly = true)
    public MemberSubscriptionInfoResponse getActiveSubscriptionInfo(User user) {
        Optional<MemberSubscription> subOpt = memberSubscriptionRepository.findCurrentActiveSubscription(user.getId(), LocalDate.now(paymentClock));
        if (subOpt.isEmpty()) {
            return MemberSubscriptionInfoResponse.builder()
                    .hasActivePackage(false)
                    .displayBanner("Chưa có gói hội viên khả dụng")
                    .build();
        }

        MemberSubscription sub = subOpt.get();
        int total = sub.getMembershipPackage() != null ? sub.getMembershipPackage().getIncludedClasses() : 0;
        long used = classBookingRepository.countUsedPackageSessions(
                user.getId(),
                sub.getStartDate().atStartOfDay(),
                sub.getEndDate().atTime(23, 59, 59)
        );
        int remaining = Math.max(0, total - (int) used);

        String packageName = sub.getMembershipPackage() != null ? sub.getMembershipPackage().getName() : "Gói hội viên";
        String banner = String.format("Gói hội viên khả dụng: %s (Còn %d buổi)", packageName, remaining);

        return MemberSubscriptionInfoResponse.builder()
                .hasActivePackage(true)
                .subscriptionId(sub.getId())
                .packageId(sub.getMembershipPackage() != null ? sub.getMembershipPackage().getId() : null)
                .packageName(packageName)
                .totalSessions(total)
                .usedSessions((int) used)
                .remainingSessions(remaining)
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .displayBanner(banner)
                .build();
    }

    User findMemberUser(String principal) {
        if (!StringUtils.hasText(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện");
        }

        User member;
        try {
            Integer userId = Integer.valueOf(principal);
            member = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin tài khoản"));
        } catch (NumberFormatException ignored) {
            member = userRepository.findByUsernameIgnoreCase(principal)
                    .or(() -> userRepository.findByEmailIgnoreCase(principal))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin tài khoản"));
        }
        if (member.getRole() == null || !"Member".equalsIgnoreCase(member.getRole().getRoleName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chức năng này chỉ dành cho Member");
        }
        return member;
    }

    private Optional<ClassSession> nextBookableSession(ClassEntity classEntity, LocalDateTime now) {
        if (!ClassSchedule.isOpen(classEntity) || ClassSchedule.expired(classEntity, now.toLocalDate())) return Optional.empty();
        return classSessionRepository.findUpcomingScheduledSessions(classEntity.getId(), now.toLocalDate()).stream()
                .filter(s -> ClassSchedule.bookable(classEntity, s, now))
                .min(java.util.Comparator.comparing((ClassSession s) -> s.getDate()).thenComparing(ClassSession::getStartTime));
    }

    private List<ClassSession> remainingSessions(ClassEntity classEntity, LocalDateTime now) {
        List<ClassSession> sessions = classSessionRepository.findUpcomingScheduledSessions(classEntity.getId(), now.toLocalDate())
                .stream().filter(s -> ClassSchedule.bookable(classEntity, s, now))
                .sorted(Comparator.comparing(ClassSession::getDate).thenComparing(ClassSession::getStartTime)).toList();
        if (sessions.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không còn buổi học hợp lệ trong thời hạn lớp");
        return sessions;
    }

    private boolean isPackagePayment(String method) {
        String s = method.trim().toLowerCase(Locale.ROOT);
        return s.contains("package") || s.contains("gói") || s.contains("goi");
    }

    private boolean isDirectPayment(String method) {
        String s = method.trim().toLowerCase(Locale.ROOT);
        return s.contains("direct") || s.contains("trực tiếp") || s.contains("truc tiep")
                || s.contains("cash") || s.contains("bank") || s.contains("card") || s.contains("wallet");
    }

    private String normalizePaymentChannel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "Bank_Transfer";
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.contains("cash") || s.contains("tiền mặt")) return "Cash";
        if (s.contains("card") || s.contains("thẻ")) return "Card";
        if (s.contains("wallet") || s.contains("ví")) return "E_Wallet";
        return "Bank_Transfer";
    }

    private String resolveBookingStatusQuery(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status) || "tất cả".equalsIgnoreCase(status)) {
            return null;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        if (s.contains("ongoing") || s.contains("diễn ra") || s.contains("dien ra") || s.contains("booked") || s.contains("active")) {
            return "Booked";
        }
        if (s.contains("completed") || s.contains("hoàn thành") || s.contains("hoan thanh")) {
            return "Completed";
        }
        if (s.contains("cancel") || s.contains("hủy") || s.contains("huy")) {
            return "Cancelled";
        }
        return status.trim();
    }

    private String mapBookingDisplayStatus(String raw) {
        if (raw == null) return "Đang diễn ra";
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "booked" -> "Đang diễn ra";
            case "pending" -> "Chờ thanh toán";
            case "completed" -> "Đã hoàn thành";
            case "cancelled" -> "Đã hủy";
            default -> raw;
        };
    }

    private String resolveCapacityUnit(String subjectName, String category) {
        String combined = (subjectName + " " + category).toLowerCase(Locale.ROOT);
        if (combined.contains("yoga") || combined.contains("thiền") || combined.contains("thien")) {
            return "thảm";
        }
        if (combined.contains("pilates")) {
            return "máy";
        }
        return "chỗ";
    }

    private String formatScheduleWithDays(LocalTime start, LocalTime end, String days) {
        String range = formatTimeRange(start, end);
        if (StringUtils.hasText(days)) {
            return String.format("%s (%s)", range, String.join(", ", ClassSchedule.normalizeDays(days)));
        }
        return range;
    }

    private String formatTimeRange(LocalTime start, LocalTime end) {
        if (start != null && end != null) {
            return String.format("%02d:%02d - %02d:%02d", start.getHour(), start.getMinute(), end.getHour(), end.getMinute());
        }
        return "Linh hoạt";
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        return CURRENCY_FORMAT.format(amount);
    }

    private String getInitials(String fullName) {
        if (!StringUtils.hasText(fullName)) return "HLV";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private void logActivity(User user, String action, String target, String desc) {
        try {
            ActivityLog log = ActivityLog.builder()
                    .user(user)
                    .actionType(action)
                    .targetEntity(target)
                    .description(desc)
                    .createdAt(LocalDateTime.now())
                    .build();
            activityLogRepository.save(log);
        } catch (Exception ignored) {
            // Không làm gián đoạn flow chính nếu bảng log có vấn đề
        }
    }
}
