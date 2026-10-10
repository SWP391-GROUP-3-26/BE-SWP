package com.swp391.beswp.service;

import com.swp391.beswp.dto.AvailableClassCardResponse;
import com.swp391.beswp.dto.AvailableClassListResponse;
import com.swp391.beswp.dto.ClassConfirmationDetailResponse;
import com.swp391.beswp.dto.MemberClassBookingRequest;
import com.swp391.beswp.dto.MemberClassBookingResponse;
import com.swp391.beswp.dto.MemberSubscriptionInfoResponse;
import com.swp391.beswp.dto.MyClassItemResponse;
import com.swp391.beswp.dto.MyClassListResponse;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

        List<ClassBooking> bookings = classBookingRepository.findMyBookings(user.getId(), resolvedStatus, keyword);

        List<MyClassItemResponse> items = bookings.stream().map(b -> {
            ClassSession session = b.getSession();
            ClassEntity classEntity = session.getClassEntity();

            boolean hasDirectPayment = paymentRepository.existsByBooking_IdAndStatusIgnoreCase(b.getId(), "Paid");
            String paymentMethod = hasDirectPayment ? "Thanh toán trực tiếp" : "Trừ gói hội viên";

            String rawStatus = b.getStatus();
            String displayStatus = mapBookingDisplayStatus(rawStatus);
            boolean canCancel = "Booked".equalsIgnoreCase(rawStatus);
            boolean canRebook = "Completed".equalsIgnoreCase(rawStatus) || "Cancelled".equalsIgnoreCase(rawStatus);

            String coachName = classEntity.getCoach() != null ? "HLV " + classEntity.getCoach().getFullName() : "Chưa cập nhật";
            String coachAvatar = classEntity.getCoach() != null ? getInitials(classEntity.getCoach().getFullName()) : "HLV";

            String schedule = StringUtils.hasText(classEntity.getDaysOfWeek()) ? classEntity.getDaysOfWeek() : "Theo lịch";
            String timeSlot = formatTimeRange(classEntity.getStartTime(), classEntity.getEndTime());

            return MyClassItemResponse.builder()
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
                    .canCancel(canCancel)
                    .canRebook(canRebook)
                    .bookingDatetime(b.getBookingDatetime())
                    .paymentMethod(paymentMethod)
                    .build();
        }).toList();

        long total = classBookingRepository.countByUser_Id(user.getId());
        long ongoing = classBookingRepository.countByUserIdAndStatus(user.getId(), "Booked");
        long completed = classBookingRepository.countByUserIdAndStatus(user.getId(), "Completed");
        long cancelled = classBookingRepository.countByUserIdAndStatus(user.getId(), "Cancelled");

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
            if (!"Active".equalsIgnoreCase(cStatus) && !"Open".equalsIgnoreCase(cStatus)) {
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

            long enrolledCount = classBookingRepository.countActiveMembersByClassId(c.getId());
            int remainingSlots = Math.max(0, c.getMaxCapacity() - (int) enrolledCount);
            boolean isEnrolled = classBookingRepository.existsActiveBookingByUserIdAndClassId(user.getId(), c.getId());
            boolean canRegister = remainingSlots > 0 && !isEnrolled;

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
                    .daysOfWeek(c.getDaysOfWeek())
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

        MemberSubscriptionInfoResponse subInfo = getActiveSubscriptionInfo(user);

        BigDecimal price = classEntity.getPrice() != null ? classEntity.getPrice() : BigDecimal.ZERO;
        String priceFormatted = formatCurrency(price) + " đ";

        String schedule = formatScheduleWithDays(classEntity.getStartTime(), classEntity.getEndTime(), classEntity.getDaysOfWeek());
        String coachName = classEntity.getCoach() != null ? classEntity.getCoach().getFullName() : "Chưa cập nhật";
        String roomName = classEntity.getRoom() != null ? classEntity.getRoom().getName() : "Chưa xác định";

        String description = (classEntity.getSubject() != null && StringUtils.hasText(classEntity.getSubject().getDescription()))
                ? classEntity.getSubject().getDescription()
                : "Chuỗi bài tập bài bản được hướng dẫn trực tiếp bởi Huấn luyện viên, giúp nâng cao thể lực và sự dẻo dai.";

        boolean eligiblePackage = subInfo.isHasActivePackage() && subInfo.getRemainingSessions() != null && subInfo.getRemainingSessions() > 0;
        String packageSubtitle = eligiblePackage
                ? "Khả dụng: " + subInfo.getRemainingSessions() + " buổi"
                : (subInfo.isHasActivePackage() ? "Không khả dụng (Đã hết buổi)" : "Chưa có gói hội viên khả dụng");

        ClassConfirmationDetailResponse.PackagePaymentOption packageOption = ClassConfirmationDetailResponse.PackagePaymentOption.builder()
                .eligible(eligiblePackage)
                .title("Trừ gói hội viên")
                .subtitle(packageSubtitle)
                .remainingSessions(subInfo.getRemainingSessions())
                .packageName(subInfo.getPackageName())
                .note(eligiblePackage ? "Mỗi lần đăng ký sẽ trừ 1 buổi từ gói hội viên của bạn" : "Bạn cần mua hoặc gia hạn gói hội viên để sử dụng hình thức này")
                .build();

        ClassConfirmationDetailResponse.DirectPaymentOption directOption = ClassConfirmationDetailResponse.DirectPaymentOption.builder()
                .available(true)
                .title("Thanh toán trực tiếp")
                .subtitle("Thẻ / QR / Chuyển khoản")
                .amount(price)
                .amountFormatted(priceFormatted)
                .build();

        return ClassConfirmationDetailResponse.builder()
                .success(true)
                .message("Lấy thông tin xác nhận đặt lớp thành công")
                .classId(classEntity.getId())
                .classCode(String.format("CLS-%03d", classEntity.getId()))
                .className(classEntity.getName())
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
    public MemberClassBookingResponse registerClass(String principal, Integer targetClassId, MemberClassBookingRequest request) {
        User user = findMemberUser(principal);

        Integer classId = targetClassId != null ? targetClassId : request.getClassId();
        if (classId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã lớp học (classId) không được để trống");
        }

        ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học với mã: " + classId));

        String status = classEntity.getStatus() != null ? classEntity.getStatus().trim() : "";
        if (!"Active".equalsIgnoreCase(status) && !"Open".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp học hiện tại không mở đăng ký (Trạng thái: " + status + ")");
        }

        if (classBookingRepository.existsActiveBookingByUserIdAndClassId(user.getId(), classEntity.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã đăng ký lớp học này rồi. Vui lòng kiểm tra mục 'Lớp học của tôi'");
        }

        long activeMembers = classBookingRepository.countActiveMembersByClassId(classEntity.getId());
        if (activeMembers >= classEntity.getMaxCapacity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp học đã đủ số lượng học viên tối đa (" + classEntity.getMaxCapacity() + " học viên)");
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

        MemberSubscription activeSub = null;
        Integer remainingSessionsAfter = null;

        if (isPackage) {
            Optional<MemberSubscription> subOpt = memberSubscriptionRepository.findCurrentActiveSubscription(user.getId(), LocalDate.now());
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
            if (remaining <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gói hội viên '" + activeSub.getMembershipPackage().getName() + "' của bạn đã hết số buổi khả dụng (còn 0 buổi). Vui lòng chọn 'Thanh toán trực tiếp'");
            }
            remainingSessionsAfter = remaining - 1;
        }

        ClassSession session = resolveOrCreateClassSession(classEntity);

        ClassBooking booking = ClassBooking.builder()
                .user(user)
                .session(session)
                .bookingDatetime(LocalDateTime.now())
                .status("Booked")
                .build();
        ClassBooking savedBooking = classBookingRepository.save(booking);

        BigDecimal paymentAmount = null;
        if (isDirect) {
            paymentAmount = classEntity.getPrice() != null ? classEntity.getPrice() : BigDecimal.ZERO;
            String channel = normalizePaymentChannel(request.getPaymentChannel());

            Payment payment = Payment.builder()
                    .booking(savedBooking)
                    .paymentType("Class_Booking")
                    .amount(paymentAmount)
                    .method(channel)
                    .paymentDate(LocalDateTime.now())
                    .status("Paid")
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            paymentRepository.save(payment);
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
                .success(true)
                .message("Đăng ký lớp học thành công! Chúc bạn có buổi tập vui vẻ và hiệu quả.")
                .bookingId(savedBooking.getId())
                .classId(classEntity.getId())
                .classCode(String.format("CLS-%03d", classEntity.getId()))
                .className(classEntity.getName())
                .schedule(schedule)
                .roomName(classEntity.getRoom() != null ? classEntity.getRoom().getName() : "")
                .coachName(classEntity.getCoach() != null ? classEntity.getCoach().getFullName() : "")
                .paymentMethod(paymentMethodDisplay)
                .remainingPackageSessions(remainingSessionsAfter)
                .paymentAmount(paymentAmount)
                .bookingStatus("Đang diễn ra")
                .bookingDatetime(savedBooking.getBookingDatetime())
                .build();
    }

    /**
     * Hủy đăng ký lớp học (Thao tác ở màn hình Lớp học của tôi).
     */
    @Transactional
    public void cancelClassBooking(String principal, Integer bookingId) {
        User user = findMemberUser(principal);

        ClassBooking booking = classBookingRepository.findByIdAndUser_Id(bookingId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin đăng ký lớp học này"));

        if ("Cancelled".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp học này đã được hủy trước đó");
        }

        if ("Completed".equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Buổi học đã hoàn thành, không thể hủy");
        }

        booking.setStatus("Cancelled");
        classBookingRepository.save(booking);

        Optional<Payment> paymentOpt = paymentRepository.findByBooking_Id(booking.getId());
        paymentOpt.ifPresent(p -> {
            p.setStatus("Refunded");
            paymentRepository.save(p);
        });

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
        Optional<MemberSubscription> subOpt = memberSubscriptionRepository.findCurrentActiveSubscription(user.getId(), LocalDate.now());
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

    private User findMemberUser(String principal) {
        if (!StringUtils.hasText(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện");
        }

        try {
            Integer userId = Integer.valueOf(principal);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin tài khoản"));
        } catch (NumberFormatException ignored) {
            return userRepository.findByUsernameIgnoreCase(principal)
                    .or(() -> userRepository.findByEmailIgnoreCase(principal))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông tin tài khoản"));
        }
    }

    private ClassSession resolveOrCreateClassSession(ClassEntity classEntity) {
        List<ClassSession> upcoming = classSessionRepository.findUpcomingScheduledSessions(classEntity.getId(), LocalDate.now());
        if (!upcoming.isEmpty()) {
            return upcoming.get(0);
        }

        LocalDate nextDate = calculateNextSessionDate(classEntity);
        LocalTime start = classEntity.getStartTime() != null ? classEntity.getStartTime() : LocalTime.of(7, 0);
        LocalTime end = classEntity.getEndTime() != null ? classEntity.getEndTime() : start.plusMinutes(75);

        Optional<ClassSession> existing = classSessionRepository.findByClassEntity_IdAndDateAndStartTime(classEntity.getId(), nextDate, start);
        if (existing.isPresent()) {
            return existing.get();
        }

        ClassSession newSession = ClassSession.builder()
                .classEntity(classEntity)
                .date(nextDate)
                .startTime(start)
                .endTime(end)
                .status("Scheduled")
                .build();

        return classSessionRepository.save(newSession);
    }

    private LocalDate calculateNextSessionDate(ClassEntity classEntity) {
        LocalDate today = LocalDate.now();
        if (classEntity.getDate() != null && !classEntity.getDate().isBefore(today)) {
            return classEntity.getDate();
        }

        String days = classEntity.getDaysOfWeek();
        if (!StringUtils.hasText(days)) {
            return today;
        }

        Map<String, DayOfWeek> dayMap = Map.of(
                "T2", DayOfWeek.MONDAY,
                "T3", DayOfWeek.TUESDAY,
                "T4", DayOfWeek.WEDNESDAY,
                "T5", DayOfWeek.THURSDAY,
                "T6", DayOfWeek.FRIDAY,
                "T7", DayOfWeek.SATURDAY,
                "CN", DayOfWeek.SUNDAY
        );

        List<DayOfWeek> targetDays = new ArrayList<>();
        for (String part : days.split("[,\\s-]+")) {
            String upper = part.trim().toUpperCase(Locale.ROOT);
            if (dayMap.containsKey(upper)) {
                targetDays.add(dayMap.get(upper));
            }
        }

        if (targetDays.isEmpty()) {
            return today;
        }

        for (int i = 0; i <= 7; i++) {
            LocalDate candidate = today.plusDays(i);
            if (targetDays.contains(candidate.getDayOfWeek())) {
                if (i == 0 && classEntity.getStartTime() != null && classEntity.getStartTime().isBefore(LocalTime.now())) {
                    continue;
                }
                return candidate;
            }
        }

        return today.plusDays(1);
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
