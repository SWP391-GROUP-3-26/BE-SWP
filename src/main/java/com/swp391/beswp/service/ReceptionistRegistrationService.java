package com.swp391.beswp.service;

import com.swp391.beswp.dto.ReceptionistClassRegistrationRequest;
import com.swp391.beswp.dto.ReceptionistPackageRegistrationRequest;
import com.swp391.beswp.dto.MemberPackageRegistrationRequest;
import com.swp391.beswp.dto.MemberPackageRenewalOverviewResponse;
import com.swp391.beswp.dto.MemberPackageRenewalPreviewRequest;
import com.swp391.beswp.dto.MemberPackageRenewalPreviewResponse;
import com.swp391.beswp.dto.MemberPackageSubscriptionResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassCatalogResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassOption;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassRegistrationPreview;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassRegistrationResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.PackageCatalogResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.PackageOption;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.PackageRegistrationResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.SessionOption;
import com.swp391.beswp.entity.ClassBooking;
import com.swp391.beswp.entity.ClassEntity;
import com.swp391.beswp.entity.ClassSession;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.MembershipIncludedSport;
import com.swp391.beswp.entity.MembershipPackage;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.ClassBookingRepository;
import com.swp391.beswp.repository.ClassRepository;
import com.swp391.beswp.repository.ClassSessionRepository;
import com.swp391.beswp.repository.MemberPackageSubscriptionRepository;
import com.swp391.beswp.repository.MemberSubscriptionRepository;
import com.swp391.beswp.repository.MembershipPackageRepository;
import com.swp391.beswp.repository.MembershipIncludedSportRepository;
import com.swp391.beswp.repository.PackageChoiceGroupRepository;
import com.swp391.beswp.repository.PackageIncludedSportRepository;
import com.swp391.beswp.repository.PaymentRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceptionistRegistrationService {

    private static final List<String> COUNTER_PAYMENT_CHANNELS =
            List.of("Cash", "Card", "E_Wallet");

    private final UserRepository userRepository;
    private final MembershipPackageRepository packageRepository;
    private final MemberPackageSubscriptionRepository packageSubscriptionRepository;
    private final MemberSubscriptionRepository memberSubscriptionRepository;
    private final ClassRepository classRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ClassBookingRepository classBookingRepository;
    private final PaymentRepository paymentRepository;
    private final ClassReservationService classReservationService;
    private final AuditLogService auditLogService;
    private final Clock paymentClock;
    private final MemberClassBookingService memberBookings;
    private final MemberPackageService memberPackages;
    private final MemberPaymentService memberPayments;
    private final OnlineSubscriptionPaymentService subscriptionPayments;
    private final VnpayService vnpay;
    private final SepayService sepay;
    private final PackageChoiceGroupRepository choiceGroups;
    private final PackageIncludedSportRepository includedSports;
    private final MembershipIncludedSportRepository includedSnapshots;

    @Transactional(readOnly = true)
    public PackageCatalogResponse getPackages(Authentication authentication, String search) {
        requireReceptionist(authentication);
        String keyword = normalize(search);
        List<PackageOption> data = packageRepository.searchPackages(keyword, "Active").stream()
                .map(p -> new PackageOption(p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                        p.getDuration(), p.getIncludedClasses(), p.getBenefits(), p.getTermConditions()))
                .toList();
        return new PackageCatalogResponse(true, "Lấy danh sách gói tập thành công", data);
    }

    @Transactional(readOnly = true)
    public MemberPackageRenewalOverviewResponse getPackageRenewalOverview(
            Authentication authentication, Integer memberId) {
        requireReceptionist(authentication);
        User member = requireMember(memberId);
        return memberPackages.getRenewalOverview(member.getId().toString());
    }

    @Transactional(readOnly = true)
    public MemberPackageRenewalPreviewResponse previewPackageRenewal(
            Authentication authentication, Integer memberId, MemberPackageRenewalPreviewRequest request) {
        requireReceptionist(authentication);
        User member = requireMember(memberId);
        return memberPackages.previewRenewal(member.getId().toString(), request);
    }

    @Transactional
    public MemberPackageSubscriptionResponse renewPackage(
            Authentication authentication, Integer memberId, ReceptionistPackageRegistrationRequest request) {
        User receptionist = requireReceptionist(authentication);
        User member = requireMember(memberId);
        if (!"Cash".equals(normalizePaymentChannel(request.paymentMethod()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Gia han tai quay hien chi ho tro Cash; Card va E_Wallet can thanh toan online");
        }
        userRepository.findByIdForUpdate(member.getId()).orElseThrow();
        MemberPackageSubscriptionResponse response = memberPackages.renewPackage(member.getId().toString(),
                toMemberPackageRequest(request));
        Integer paymentId = response.getData().getPaymentId();
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setReceptionist(receptionist);
        auditLogService.recordLog(receptionist, "RENEW_MEMBERSHIP", "Payment",
                "Le tan gia han Package cho hoi vien #" + member.getId() + ", payment #" + paymentId);
        return response;
    }

    @Transactional(readOnly = true)
    public ClassCatalogResponse getClasses(Authentication authentication, String search) {
        requireReceptionist(authentication);
        LocalDateTime now = now();
        String keyword = normalize(search);
        Integer parsedId = parseOptionalId(keyword);
        Map<Integer, ClassEntity> classes = new LinkedHashMap<>();
        classRepository.searchClasses(keyword, parsedId, "Active").forEach(c -> classes.put(c.getId(), c));
        classRepository.searchClasses(keyword, parsedId, "Open").forEach(c -> classes.putIfAbsent(c.getId(), c));

        List<ClassOption> data = classes.values().stream()
                .filter(c -> ClassSchedule.isOpen(c) && !ClassSchedule.expired(c, now.toLocalDate()))
                .map(c -> toClassOption(c, now))
                .toList();
        return new ClassCatalogResponse(true, "Lấy danh sách lớp thành công", data);
    }

    @Transactional(readOnly = true)
    public ClassRegistrationPreview getClassRegistrationPreview(
            Authentication authentication,
            Integer memberId,
            Integer classId
    ) {
        requireReceptionist(authentication);
        User member = requireMember(memberId);
        ClassEntity course = classRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học"));
        LocalDateTime now = now();
        List<ClassSession> sessions = futureBookableSessions(course, now);
        List<SessionOption> sessionOptions = toSessionOptions(sessions, now, false);
        ClassOption classOption = toClassOption(course, now, sessions);

        boolean alreadyRegistered = classBookingRepository.existsActiveBookingByUserIdAndClassId(
                member.getId(), course.getId(), now);
        MemberSubscription activeSubscription = memberSubscriptionRepository
                .findCurrentActiveSubscription(member.getId(), now.toLocalDate()).orElse(null);
        int totalPackageClasses = activeSubscription == null || activeSubscription.getMembershipPackage() == null
                ? 0 : safeInt(activeSubscription.getMembershipPackage().getIncludedClasses());
        int usedPackageClasses = activeSubscription == null ? 0 : safeInt(classBookingRepository.countUsedPackageSessions(
                member.getId(), activeSubscription.getStartDate().atStartOfDay(),
                activeSubscription.getEndDate().atTime(23, 59, 59)));
        int remainingPackageClasses = Math.max(0, totalPackageClasses - usedPackageClasses);
        boolean packageTermCoversCourse = activeSubscription != null && !sessions.isEmpty()
                && !sessions.getLast().getDate().isAfter(activeSubscription.getEndDate());
        boolean canPayWithPackage = activeSubscription != null && packageTermCoversCourse
                && remainingPackageClasses >= sessions.size();
        boolean seatsAvailable = !sessions.isEmpty() && sessionOptions.stream().allMatch(s -> s.availableSeats() > 0);
        boolean canRegister = ClassSchedule.isOpen(course) && !ClassSchedule.expired(course, now.toLocalDate())
                && seatsAvailable && !alreadyRegistered;

        String message;
        if (!ClassSchedule.isOpen(course) || ClassSchedule.expired(course, now.toLocalDate())) {
            message = "Lớp hiện không mở đăng ký";
        } else if (sessions.isEmpty()) {
            message = "Lớp không còn buổi học tương lai có thể đăng ký";
        } else if (!seatsAvailable) {
            message = "Có buổi học trong lớp đã hết chỗ";
        } else if (alreadyRegistered) {
            message = "Hội viên đã có đăng ký còn hiệu lực cho lớp này";
        } else if (!canPayWithPackage) {
            message = "Có thể đăng ký và thanh toán tại quầy; gói hiện tại không đủ buổi hoặc không bao phủ hết khóa";
        } else {
            message = "Hội viên đủ điều kiện đăng ký bằng gói hoặc thanh toán tại quầy";
        }

        BigDecimal directAmount = unitPrice(course).multiply(BigDecimal.valueOf(sessions.size()));
        return new ClassRegistrationPreview(true, message, member.getId(), member.getFullName(), classOption,
                sessionOptions, alreadyRegistered, canRegister, canRegister && canPayWithPackage,
                activeSubscription == null ? null : activeSubscription.getId(),
                activeSubscription == null || activeSubscription.getMembershipPackage() == null
                        ? null : activeSubscription.getMembershipPackage().getName(),
                activeSubscription == null ? null : totalPackageClasses,
                activeSubscription == null ? null : usedPackageClasses,
                activeSubscription == null ? null : remainingPackageClasses,
                sessions.size(), directAmount, COUNTER_PAYMENT_CHANNELS);
    }

    @Transactional
    public PackageRegistrationResponse registerPackage(
            Authentication authentication,
            Integer memberId,
            ReceptionistPackageRegistrationRequest request
    ) {
        User receptionist = requireReceptionist(authentication);
        User member = requireMember(memberId);
        userRepository.findByIdForUpdate(member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội viên"));

        MembershipPackage membershipPackage = packageRepository.findById(request.packageId())
                .filter(p -> "Active".equalsIgnoreCase(p.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gói tập đang mở bán"));
        String paymentMethod = normalizePaymentChannel(request.paymentMethod());
        if (!"Cash".equals(paymentMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thanh toán online phải tạo giao dịch chờ xác nhận");
        }
        LocalDate today = LocalDate.now(paymentClock);
        subscriptionPayments.expirePending(member.getId());

        if (subscriptionPayments.hasPending(member.getId()) || packageSubscriptionRepository.hasActiveSubscription(member.getId(), today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hội viên đang có gói hoạt động; không thể đăng ký thêm gói tại thời điểm này");
        }
        if (membershipPackage.getDuration() == null || membershipPackage.getDuration() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thời hạn gói tập không hợp lệ");
        }
        if (!choiceGroups.findByPackageId(membershipPackage.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Gói có Choice Group phải được đăng ký qua luồng Member để chọn Sport đầy đủ");
        }

        LocalDate endDate;
        try {
            endDate = today.plusDays(membershipPackage.getDuration().longValue() - 1L);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời hạn gói vượt phạm vi ngày hợp lệ");
        }
        BigDecimal amount = membershipPackage.getPrice();
        if (amount == null || amount.signum() < 0 || !fitsPaymentColumn(amount)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giá gói không hợp lệ để ghi nhận thanh toán");
        }

        BigDecimal discount = membershipPackage.getDiscount() == null ? BigDecimal.ZERO : membershipPackage.getDiscount();
        BigDecimal discountAmount = amount.multiply(discount).movePointLeft(2);
        BigDecimal amountAfterDiscount = amount.subtract(discountAmount);
        MemberSubscription subscription = packageSubscriptionRepository.save(MemberSubscription.builder()
                .user(member)
                .membershipPackage(membershipPackage)
                .priceAtPurchase(amountAfterDiscount)
                .durationAtPurchase(membershipPackage.getDuration())
                .termsAtPurchase(membershipPackage.getTermConditions())
                .packageNameAtPurchase(membershipPackage.getName())
                .startDate(today)
                .endDate(endDate)
                .status("Active")
                .build());
        for (var included : includedSports.findByPackageId(membershipPackage.getId())) {
            includedSnapshots.save(MembershipIncludedSport.builder().subscription(subscription)
                    .subject(included.getSubject()).subjectNameAtPurchase(included.getSubject().getName()).build());
        }
        LocalDateTime registeredAt = now();
        Payment payment = paymentRepository.save(Payment.builder()
                .subscription(subscription)
                .receptionist(receptionist)
                .paymentType("Subscription")
                .amount(amountAfterDiscount)
                .method(paymentMethod)
                .paymentDate(registeredAt)
                .status("Paid")
                .discountAmount(discountAmount)
                .build());

        auditLogService.recordLog(receptionist, "PURCHASE_MEMBERSHIP", "Payment",
                String.format("Lễ tân %s đăng ký gói '%s' cho hội viên %s, số tiền %s VND (Phương thức: %s)",
                        receptionist.getFullName(), membershipPackage.getName(), member.getFullName(),
                        formatCurrency(amount), paymentMethod));

        return new PackageRegistrationResponse(true, "Đăng ký gói tập và ghi nhận thanh toán thành công",
                member.getId(), member.getFullName(), subscription.getId(), membershipPackage.getId(),
                membershipPackage.getName(), today, endDate, payment.getId(), amount, paymentMethod, payment.getStatus());
    }

    @Transactional
    public ClassRegistrationResponse registerClass(
            Authentication authentication,
            Integer memberId,
            ReceptionistClassRegistrationRequest request
    ) {
        User receptionist = requireReceptionist(authentication);
        User member = requireMember(memberId);
        userRepository.findByIdForUpdate(member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội viên"));

        ClassEntity course = classRepository.findByIdForUpdate(request.classId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học"));
        LocalDateTime registeredAt = now();
        if (!ClassSchedule.isOpen(course) || ClassSchedule.expired(course, registeredAt.toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp hiện không mở đăng ký");
        }

        classReservationService.expirePending(course.getId(), registeredAt);
        if (classBookingRepository.existsActiveBookingByUserIdAndClassId(member.getId(), course.getId(), registeredAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hội viên đã đăng ký lớp này rồi");
        }

        List<ClassSession> sessions = futureBookableSessions(course, registeredAt);
        if (sessions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp không còn buổi học tương lai có thể đăng ký");
        }
        List<SessionOption> beforeRegistration = toSessionOptions(sessions, registeredAt, false);
        SessionOption fullSession = beforeRegistration.stream().filter(s -> s.availableSeats() <= 0).findFirst().orElse(null);
        if (fullSession != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Buổi học ngày " + fullSession.date() + " đã hết chỗ");
        }

        String method = normalizeClassPaymentMethod(request.paymentMethod());
        boolean packagePayment = "PACKAGE".equals(method);
        String paymentChannel = null;
        MemberSubscription activeSubscription = null;
        Integer packageClassesRemaining = null;
        if (packagePayment) {
            if (StringUtils.hasText(request.paymentChannel())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Không gửi paymentChannel khi paymentMethod là PACKAGE");
            }
            activeSubscription = memberSubscriptionRepository
                    .findCurrentActiveSubscription(member.getId(), registeredAt.toLocalDate())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Hội viên không có gói tập đang hoạt động"));
            int total = activeSubscription.getMembershipPackage() == null ? 0
                    : safeInt(activeSubscription.getMembershipPackage().getIncludedClasses());
            long used = classBookingRepository.countUsedPackageSessions(member.getId(),
                    activeSubscription.getStartDate().atStartOfDay(),
                    activeSubscription.getEndDate().atTime(23, 59, 59));
            int remaining = Math.max(0, total - safeInt(used));
            if (remaining < sessions.size() || sessions.getLast().getDate().isAfter(activeSubscription.getEndDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Gói hội viên không đủ số buổi hoặc hết hạn trước buổi cuối của lớp");
            }
            packageClassesRemaining = remaining - sessions.size();
        } else {
            paymentChannel = normalizePaymentChannel(request.paymentChannel());
            if (!"Cash".equals(paymentChannel)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thanh toán online phải tạo giao dịch chờ xác nhận");
            }
        }

        BigDecimal paymentAmount = packagePayment ? null
                : unitPrice(course).multiply(BigDecimal.valueOf(sessions.size()));
        if (paymentAmount != null && (paymentAmount.signum() < 0 || !fitsPaymentColumn(paymentAmount))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng học phí vượt giới hạn thanh toán");
        }

        String registrationCode = UUID.randomUUID().toString();
        List<ClassBooking> bookings = new ArrayList<>();
        for (ClassSession session : sessions) {
            bookings.add(classBookingRepository.save(ClassBooking.builder()
                    .user(member)
                    .session(session)
                    .registrationCode(registrationCode)
                    .bookingDatetime(registeredAt)
                    .status("Booked")
                    .paymentMethod(packagePayment ? "PACKAGE" : "DIRECT")
                    .build()));
        }
        ClassBooking firstBooking = bookings.getFirst();

        Payment payment = null;
        if (!packagePayment) {
            payment = paymentRepository.save(Payment.builder()
                    .booking(firstBooking)
                    .receptionist(receptionist)
                    .paymentType("Class_Booking")
                    .amount(paymentAmount)
                    .method(paymentChannel)
                    .paymentDate(registeredAt)
                    .status("Paid")
                    .discountAmount(BigDecimal.ZERO)
                    .build());
        }

        auditLogService.recordLog(receptionist, "BOOK_CLASS", "Class_Booking",
                String.format("Lễ tân %s đăng ký hội viên %s vào lớp '%s' (%d buổi, hình thức: %s)",
                        receptionist.getFullName(), member.getFullName(), course.getName(), sessions.size(),
                        packagePayment ? "PACKAGE" : "DIRECT - " + paymentChannel));
        if (payment != null) {
            auditLogService.recordLog(receptionist, "CREATE_PAYMENT", "Payment",
                    String.format("Lễ tân %s thu %s VND cho đăng ký lớp '%s' của hội viên %s (Phương thức: %s)",
                            receptionist.getFullName(), formatCurrency(paymentAmount), course.getName(),
                            member.getFullName(), paymentChannel));
        }

        List<SessionOption> afterRegistration = beforeRegistration.stream()
                .map(s -> new SessionOption(s.sessionId(), s.date(), s.startTime(), s.endTime(), s.capacity(),
                        Math.max(0, s.availableSeats() - 1)))
                .toList();
        return new ClassRegistrationResponse(true, "Đăng ký lớp cho hội viên thành công",
                firstBooking.getId(), firstBooking.getId(), member.getId(), member.getFullName(),
                course.getId(), course.getName(), method, paymentChannel, sessions.size(), afterRegistration,
                activeSubscription == null ? null : activeSubscription.getId(),
                activeSubscription == null || activeSubscription.getMembershipPackage() == null
                        ? null : activeSubscription.getMembershipPackage().getName(),
                packageClassesRemaining, payment == null ? null : payment.getId(), paymentAmount,
                payment == null ? null : payment.getStatus(), registeredAt);
    }

    @Transactional
    public Object registerOnlineClass(Authentication authentication, Integer memberId,
            ReceptionistClassRegistrationRequest request, String ipAddress) {
        User actor = requireReceptionist(authentication);
        User member = requireMember(memberId);
        String channel = normalizePaymentChannel(request.paymentChannel());
        if (!"DIRECT".equalsIgnoreCase(request.paymentMethod()) || !List.of("Card", "E_Wallet").contains(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kênh thanh toán online không hợp lệ");
        }
        var body = com.swp391.beswp.dto.MemberClassBookingRequest.builder()
                .classId(request.classId()).paymentMethod("DIRECT").paymentChannel(channel).build();
        var result = memberBookings.registerClass(member.getId().toString(), request.classId(), body, ipAddress);
        Payment payment = paymentRepository.findById(result.getPaymentId()).orElseThrow();
        payment.setReceptionist(actor);
        auditLogService.recordLog(actor, "BOOK_CLASS", "Payment", "Tạo thanh toán #" + payment.getId()
                + " cho hội viên #" + memberId + " - " + member.getFullName());
        return result;
    }

    @Transactional
    public Object registerOnlinePackage(Authentication authentication, Integer memberId,
            ReceptionistPackageRegistrationRequest request, String ipAddress) {
        User actor = requireReceptionist(authentication);
        User member = requireMember(memberId);
        userRepository.findByIdForUpdate(memberId).orElseThrow();
        String channel = normalizePaymentChannel(request.paymentMethod());
        if (!List.of("Card", "E_Wallet").contains(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kênh thanh toán online không hợp lệ");
        }
        if ("Card".equals(channel)) vnpay.requireConfigured(); else sepay.requireConfigured();
        subscriptionPayments.expirePending(memberId);
        LocalDate today = LocalDate.now(paymentClock);
        if (packageSubscriptionRepository.hasActiveSubscription(memberId, today) || subscriptionPayments.hasPending(memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hội viên đang có gói hoạt động hoặc giao dịch gói đang chờ thanh toán");
        }
        MembershipPackage pack = packageRepository.findById(request.packageId())
                .filter(p -> "Active".equalsIgnoreCase(p.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gói đang mở bán"));
        BigDecimal amount = pack.getPrice();
        if (pack.getDuration() == null || pack.getDuration() <= 0 || amount == null || amount.signum() <= 0
                || !fitsPaymentColumn(amount) || amount.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giá hoặc thời hạn gói không hợp lệ để thanh toán online");
        }
        var subscription = packageSubscriptionRepository.save(MemberSubscription.builder().user(member)
                .membershipPackage(pack).startDate(today).endDate(today.plusDays(pack.getDuration() - 1L))
                .status("Pending").build());
        Payment payment = paymentRepository.save(Payment.builder().subscription(subscription).receptionist(actor)
                .paymentType("Subscription").amount(amount).method(channel).paymentDate(now())
                .expiresAt(now().plusMinutes(VnpayService.PAYMENT_TIMEOUT_MINUTES)).status("Pending").build());
        String url = "Card".equals(channel) ? vnpay.createPaymentUrl(payment, ipAddress) : "";
        auditLogService.recordLog(actor, "PURCHASE_MEMBERSHIP", "Payment", "Tạo thanh toán #" + payment.getId()
                + " cho hội viên #" + memberId + " - " + member.getFullName());
        return Map.of("success", true, "paymentId", payment.getId(), "paymentStatus", "Pending",
                "paymentUrl", url, "memberId", memberId);
    }

    @Transactional(readOnly = true)
    public Object getMemberClasses(Authentication authentication, Integer memberId) {
        requireReceptionist(authentication);
        User member = requireMember(memberId);
        return memberBookings.getMyClasses(member.getId().toString(), "all", null);
    }

    @Transactional
    public Object getMemberPackages(Authentication authentication, Integer memberId) {
        requireReceptionist(authentication);
        User member = requireMember(memberId);
        userRepository.findByIdForUpdate(memberId).orElseThrow();
        subscriptionPayments.expirePending(memberId);
        return memberPackages.getMySubscriptions(member.getId().toString(), null, 0, 100);
    }

    @Transactional
    public Object cancelPendingPayment(Authentication authentication, Integer memberId, Integer paymentId) {
        User actor = requireReceptionist(authentication);
        User member = requireMember(memberId);
        userRepository.findByIdForUpdate(memberId).orElseThrow();
        Integer owner = paymentRepository.findOnlineSubscriptionMemberId(paymentId).orElse(null);
        Payment payment;
        if (owner != null) {
            if (!owner.equals(memberId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thanh toán của hội viên");
            payment = subscriptionPayments.lock(paymentId);
        } else {
            Integer classId = paymentRepository.findOnlineClassId(paymentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thanh toán online"));
            classRepository.findByIdForUpdate(classId).orElseThrow();
            payment = paymentRepository.findById(paymentId).orElseThrow();
            if (!payment.getBooking().getUser().getId().equals(memberId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thanh toán không thuộc hội viên");
            }
        }
        if (!"Pending".equals(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thanh toán đã thay đổi trạng thái. Hãy kiểm tra lại trước khi thao tác");
        }
        if (payment.getSubscription() != null) {
            payment.setStatus("Failed");
            payment.getSubscription().setStatus("Cancelled");
        } else {
            memberBookings.cancelClassBooking(member.getId().toString(), payment.getBooking().getId());
        }
        auditLogService.recordLog(actor, "CANCEL_PAYMENT", "Payment", "Hủy yêu cầu thanh toán #" + paymentId + " của hội viên #" + memberId);
        return Map.of("success", true, "message", "Đã hủy yêu cầu chờ thanh toán. Giao dịch chuyển tiền đến sau sẽ được đánh dấu cần đối soát, không tự hoàn tiền.");
    }

    @Transactional
    public Object getPayment(Authentication authentication, Integer memberId, Integer paymentId) {
        requireReceptionist(authentication);
        User member = requireMember(memberId);
        return memberPayments.getPayment(member.getId().toString(), paymentId);
    }

    @Transactional
    public Object cancelClass(Authentication authentication, Integer memberId, Integer bookingId) {
        User actor = requireReceptionist(authentication);
        User member = requireMember(memberId);
        userRepository.findByIdForUpdate(memberId).orElseThrow();
        memberBookings.cancelClassBooking(member.getId().toString(), bookingId);
        auditLogService.recordLog(actor, "CANCEL_BOOKING", "Class_Booking",
                "Hủy đăng ký #" + bookingId + " của hội viên #" + memberId + " - " + member.getFullName());
        return Map.of("success", true, "message", "Đã hủy các buổi tương lai. Tiền đã thu cần đối soát, chưa được tự động hoàn.");
    }

    private User requireReceptionist(Authentication authentication) {
        boolean hasReceptionistAuthority = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_Receptionist"::equals);
        if (!hasReceptionistAuthority) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ lễ tân mới được sử dụng API đăng ký tại quầy");
        }
        Integer receptionistId = parsePrincipalId(authentication);
        User receptionist = userRepository.findByIdAndRoleRoleNameIgnoreCase(receptionistId, "Receptionist")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không có vai trò lễ tân"));
        if (!"Active".equalsIgnoreCase(receptionist.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản lễ tân không hoạt động");
        }
        return receptionist;
    }

    private User requireMember(Integer memberId) {
        if (memberId == null || memberId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "memberId không hợp lệ");
        }
        User member = userRepository.findByIdAndRoleRoleNameIgnoreCase(memberId, "Member")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hội viên"));
        if (!"Active".equalsIgnoreCase(member.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản hội viên không hoạt động");
        }
        return member;
    }

    private Integer parsePrincipalId(Authentication authentication) {
        try {
            return Integer.valueOf(authentication.getName());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản lễ tân");
        }
    }

    private ClassOption toClassOption(ClassEntity course, LocalDateTime now) {
        return toClassOption(course, now, futureBookableSessions(course, now));
    }

    private ClassOption toClassOption(ClassEntity course, LocalDateTime now, List<ClassSession> sessions) {
        List<SessionOption> sessionOptions = toSessionOptions(sessions, now, false);
        int seats = sessionOptions.isEmpty() ? 0
                : sessionOptions.stream().mapToInt(SessionOption::availableSeats).min().orElse(0);
        return new ClassOption(course.getId(), course.getName(),
                course.getSubject() == null ? null : course.getSubject().getName(),
                course.getCoach() == null ? null : course.getCoach().getFullName(),
                course.getRoom() == null ? null : course.getRoom().getName(),
                ClassSchedule.effectiveStatus(course, now.toLocalDate()), unitPrice(course), course.getMaxCapacity(),
                course.getDate(), course.getDurationWeeks(), course.getEndDate(), course.getDaysOfWeek(),
                course.getStartTime(), course.getEndTime(), sessions.size(), seats,
                !sessions.isEmpty() && seats > 0);
    }

    private List<ClassSession> futureBookableSessions(ClassEntity course, LocalDateTime now) {
        if (!ClassSchedule.isOpen(course) || ClassSchedule.expired(course, now.toLocalDate())) return List.of();
        return classSessionRepository.findUpcomingScheduledSessions(course.getId(), now.toLocalDate()).stream()
                .filter(session -> ClassSchedule.bookable(course, session, now))
                .toList();
    }

    private List<SessionOption> toSessionOptions(List<ClassSession> sessions, LocalDateTime now, boolean afterBooking) {
        return sessions.stream().map(session -> {
            int capacity = session.getClassEntity().getMaxCapacity();
            long reserved = classBookingRepository.countReservedMembersBySessionId(session.getId(), now);
            int available = Math.max(0, capacity - safeInt(reserved));
            if (afterBooking) available = Math.max(0, available - 1);
            return new SessionOption(session.getId(), session.getDate(), session.getStartTime(), session.getEndTime(),
                    capacity, available);
        }).toList();
    }

    private String normalizeClassPaymentMethod(String rawMethod) {
        if (!StringUtils.hasText(rawMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentMethod phải là PACKAGE hoặc DIRECT");
        }
        String normalized = rawMethod.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "PACKAGE", "MEMBERSHIP_PACKAGE" -> "PACKAGE";
            case "DIRECT", "DIRECT_PAYMENT" -> "DIRECT";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "paymentMethod chỉ nhận PACKAGE hoặc DIRECT");
        };
    }

    private MemberPackageRegistrationRequest toMemberPackageRequest(ReceptionistPackageRegistrationRequest request) {
        MemberPackageRegistrationRequest result = new MemberPackageRegistrationRequest();
        result.setPackageId(request.packageId());
        result.setPaymentMethod(request.paymentMethod());
        result.setRenewalStart(request.renewalStart());
        result.setChoiceGroups(request.choiceGroups());
        return result;
    }

    private String normalizePaymentChannel(String rawMethod) {
        if (!StringUtils.hasText(rawMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "paymentMethod/paymentChannel phải là Cash, Card, Bank_Transfer hoặc E_Wallet");
        }
        String normalized = rawMethod.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "_");
        return switch (normalized) {
            case "cash" -> "Cash";
            case "card" -> "Card";
            case "bank_transfer", "banktransfer" -> "Bank_Transfer";
            case "e_wallet", "ewallet", "wallet" -> "E_Wallet";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Phương thức thanh toán chỉ nhận Cash, Card, Bank_Transfer hoặc E_Wallet");
        };
    }

    private BigDecimal unitPrice(ClassEntity course) {
        return course.getPrice() == null ? BigDecimal.ZERO : course.getPrice();
    }

    private boolean fitsPaymentColumn(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() <= 2 && normalized.precision() - normalized.scale() <= 10;
    }

    private int safeInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(paymentClock).withNano(0);
    }

    private Integer parseOptionalId(String keyword) {
        if (keyword == null || !keyword.matches("\\d{1,10}")) return null;
        try {
            return Integer.valueOf(keyword);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatCurrency(BigDecimal amount) {
        return amount == null ? "0" : String.format("%,d", amount.longValue());
    }
}
