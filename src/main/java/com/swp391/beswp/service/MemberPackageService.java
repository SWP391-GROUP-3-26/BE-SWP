package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberPackageCatalogResponse;
import com.swp391.beswp.dto.MemberPackageRegistrationRequest;
import com.swp391.beswp.dto.MemberPackageRenewalOverviewResponse;
import com.swp391.beswp.dto.MemberPackageRenewalPreviewRequest;
import com.swp391.beswp.dto.MemberPackageRenewalPreviewResponse;
import com.swp391.beswp.dto.MemberPackageSubscriptionData;
import com.swp391.beswp.dto.MemberPackageSubscriptionListResponse;
import com.swp391.beswp.dto.MemberPackageSubscriptionResponse;
import com.swp391.beswp.dto.PackageResponse;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.MembershipPackage;
import com.swp391.beswp.entity.MembershipSportSelection;
import com.swp391.beswp.entity.MembershipIncludedSport;
import com.swp391.beswp.entity.PackageChoiceGroup;
import com.swp391.beswp.entity.PackageIncludedSport;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.entity.Subject;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.MemberPackagePaymentRepository;
import com.swp391.beswp.repository.MemberPackageSubscriptionRepository;
import com.swp391.beswp.repository.MembershipPackageRepository;
import com.swp391.beswp.repository.MembershipSportSelectionRepository;
import com.swp391.beswp.repository.MembershipIncludedSportRepository;
import com.swp391.beswp.repository.PackageIncludedSportRepository;
import com.swp391.beswp.repository.PackageChoiceGroupRepository;
import com.swp391.beswp.repository.PackageChoiceGroupSportRepository;
import com.swp391.beswp.repository.SubjectRepository;
import com.swp391.beswp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberPackageService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> SUBSCRIPTION_STATUSES = List.of("Active", "Pending", "Expired", "Cancelled");

    private final MembershipPackageRepository packageRepository;
    private final MemberPackageSubscriptionRepository subscriptionRepository;
    private final MemberPackagePaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final OnlineSubscriptionPaymentService subscriptionPayments;
    private final PackageChoiceGroupRepository choiceGroupRepository;
    private final PackageChoiceGroupSportRepository choiceGroupSportRepository;
    private final MembershipSportSelectionRepository selectionRepository;
    private final MembershipIncludedSportRepository includedSnapshotRepository;
    private final PackageIncludedSportRepository includedSportRepository;
    private final SubjectRepository subjectRepository;
    private final VnpayService vnpay;
    private final SepayService sepay;
    private final java.time.Clock paymentClock;

    @Transactional(readOnly = true)
    public MemberPackageCatalogResponse getAvailablePackages(String principal, String search, int page, int size) {
        requireMember(principal);
        validatePage(page, size);

        String keyword = normalize(search);
        List<MembershipPackage> packages = packageRepository.searchPackages(keyword, "Active");
        long from = (long) page * size;
        List<MembershipPackage> pageItems = from >= packages.size()
                ? List.of()
                : packages.subList((int) from, Math.min((int) from + size, packages.size()));

        return MemberPackageCatalogResponse.success(pageItems, packages.size(), page, size);
    }

    @Transactional(readOnly = true)
    public PackageResponse getAvailablePackage(String principal, Integer packageId) {
        requireMember(principal);
        if (packageId == null || packageId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "packageId không hợp lệ");
        }

        MembershipPackage membershipPackage = findActivePackage(packageId);
        return PackageResponse.success("Lấy thông tin gói dịch vụ thành công", packageData(membershipPackage));
    }

    @Transactional(readOnly = true)
    public MemberPackageRenewalOverviewResponse getRenewalOverview(String principal) {
        User member = requireMember(principal);
        LocalDate today = LocalDate.now(paymentClock);
        List<MemberSubscription> active = subscriptionRepository.findCurrentActiveSubscriptions(member.getId(), today);
        if (active.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tài khoản đang có nhiều gói ACTIVE; cần được quản trị viên xử lý trước");
        }
        MemberSubscription current = active.isEmpty() ? null : active.getFirst();
        Payment currentPayment = current == null ? null
                : paymentRepository.findFirstBySubscription_IdOrderByIdDesc(current.getId()).orElse(null);
        List<PackageResponse.PackageData> packages = packageRepository.searchPackages(null, "Active").stream()
                .map(this::packageData)
                .toList();
        return MemberPackageRenewalOverviewResponse.builder()
                .success(true)
                .message("Lấy thông tin gói dịch vụ thành công")
                .currentSubscription(current == null ? null : MemberPackageSubscriptionData.fromEntities(current, currentPayment))
                .availablePackages(packages)
                .build();
    }

    @Transactional(readOnly = true)
    public MemberPackageRenewalPreviewResponse previewRenewal(
            String principal, MemberPackageRenewalPreviewRequest request) {
        User member = requireMember(principal);
        LocalDate today = LocalDate.now(paymentClock);
        MemberSubscription current = requireSingleActiveSubscription(member.getId(), today);
        MembershipPackage selected = findActivePackage(request.getPackageId());
        validatePackageForPurchase(selected);
        validateSelections(selected.getId(), request.getChoiceGroups());
        String renewalStart = normalizeRenewalStart(request.getRenewalStart());
        if ("AFTER_CURRENT".equals(renewalStart) && subscriptionRepository.hasPendingSubscription(member.getId(), today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bạn đã có một gói PENDING sẽ bắt đầu sau gói hiện tại");
        }
        LocalDate startDate = "AFTER_CURRENT".equals(renewalStart)
                ? current.getEndDate().plusDays(1) : today;
        BigDecimal listedPrice = selected.getPrice();
        BigDecimal discountAmount = listedPrice.multiply(defaultDiscount(selected)).movePointLeft(2);
        Payment payment = paymentRepository.findFirstBySubscription_IdOrderByIdDesc(current.getId()).orElse(null);
        return MemberPackageRenewalPreviewResponse.builder()
                .success(true)
                .message("Thông tin gia hạn hợp lệ")
                .currentSubscription(MemberPackageSubscriptionData.fromEntities(current, payment))
                .selectedPackage(packageData(selected))
                .renewalStart(renewalStart)
                .newStartDate(startDate)
                .newEndDate(startDate.plusDays(selected.getDuration().longValue() - 1L))
                .listedPrice(listedPrice)
                .discountAmount(discountAmount)
                .totalAmount(listedPrice.subtract(discountAmount))
                .build();
    }

    @Transactional
    public MemberPackageSubscriptionResponse renewPackage(String principal, MemberPackageRegistrationRequest request) {
        return purchasePackage(principal, request, null, false, true);
    }

    @Transactional
    public MemberPackageSubscriptionResponse registerPackage(
            String principal,
            MemberPackageRegistrationRequest request
    ) {
        return purchasePackage(principal, request, null, false, false);
    }

    @Transactional
    public MemberPackageSubscriptionResponse registerOnlinePackage(String principal,
            MemberPackageRegistrationRequest request, String ipAddress, boolean renewal) {
        return purchasePackage(principal, request, ipAddress, true, renewal);
    }

    private MemberPackageSubscriptionResponse purchasePackage(String principal,
            MemberPackageRegistrationRequest request, String ipAddress, boolean online, boolean renewal) {
        User member = requireMember(principal);
        userRepository.findByIdForUpdate(member.getId()).orElseThrow();
        subscriptionPayments.expirePending(member.getId());
        MembershipPackage membershipPackage = findActivePackage(request.getPackageId());
        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        if (online) {
            if (!List.of("Card", "E_Wallet").contains(paymentMethod)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hội viên chỉ thanh toán qua VNPay hoặc QR SePay");
            }
            if ("Card".equals(paymentMethod)) vnpay.requireConfigured(); else sepay.requireConfigured();
        }
        LocalDate today = LocalDate.now(paymentClock);
        Map<Integer, List<Integer>> selections = validateSelections(membershipPackage.getId(), request.getChoiceGroups());

        if (subscriptionPayments.hasPending(member.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bạn đang có một thanh toán gói hội viên chờ xác nhận"
            );
        }

        List<MemberSubscription> activeSubscriptions = subscriptionRepository
                .findCurrentActiveSubscriptions(member.getId(), today);
        if (activeSubscriptions.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tài khoản đang có nhiều gói ACTIVE; cần được quản trị viên xử lý trước khi gia hạn");
        }
        String renewalStart = normalizeRenewalStart(request.getRenewalStart());
        MemberSubscription current = activeSubscriptions.isEmpty() ? null : activeSubscriptions.getFirst();
        if (renewal && current == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa có gói hội viên đang hoạt động để gia hạn");
        }
        if (online && !renewal && current != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đang có gói hoạt động; hãy sử dụng chức năng gia hạn");
        }
        if (current == null && "AFTER_CURRENT".equals(renewalStart)) {
            renewalStart = "IMMEDIATE";
        }
        if ("AFTER_CURRENT".equals(renewalStart) && subscriptionRepository.hasPendingSubscription(member.getId(), today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bạn đã có một gói PENDING sẽ bắt đầu sau gói hiện tại");
        }

        validatePackageForPurchase(membershipPackage);

        LocalDate startDate = "AFTER_CURRENT".equals(renewalStart)
                ? current.getEndDate().plusDays(1) : today;
        if (!online && current != null && "IMMEDIATE".equals(renewalStart)) {
            current.setStatus("Expired");
        }
        LocalDate endDate = startDate.plusDays(membershipPackage.getDuration().longValue() - 1L);
        BigDecimal discountAmount = membershipPackage.getPrice()
                .multiply(defaultDiscount(membershipPackage))
                .movePointLeft(2);
        BigDecimal priceAtPurchase = membershipPackage.getPrice().subtract(discountAmount);
        if (online && (priceAtPurchase.signum() <= 0 || priceAtPurchase.stripTrailingZeros().scale() > 0
                || priceAtPurchase.compareTo(new BigDecimal("9999999999")) > 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Học phí thanh toán online phải là số nguyên VND dương trong giới hạn thanh toán");
        }
        MemberSubscription subscription = MemberSubscription.builder()
                .user(member)
                .membershipPackage(membershipPackage)
                .priceAtPurchase(priceAtPurchase)
                .durationAtPurchase(membershipPackage.getDuration())
                .termsAtPurchase(membershipPackage.getTermConditions())
                .packageNameAtPurchase(membershipPackage.getName())
                .startDate(startDate)
                .endDate(endDate)
                .status(online || "AFTER_CURRENT".equals(renewalStart) ? "Pending" : "Active")
                .build();
        MemberSubscription savedSubscription = subscriptionRepository.save(subscription);
        for (PackageIncludedSport included : includedSportRepository.findByPackageId(membershipPackage.getId())) {
            selectionSnapshot(savedSubscription, included);
        }
        selections.forEach((groupId, subjectIds) -> {
            PackageChoiceGroup group = choiceGroupRepository.findById(groupId).orElseThrow();
            subjectIds.forEach(subjectId -> selectionRepository.save(MembershipSportSelection.builder()
                    .subscription(savedSubscription).choiceGroupId(group.getId())
                    .choiceGroupNameAtPurchase(group.getGroupName())
                    .subject(subjectRepository.findById(subjectId).orElseThrow())
                    .subjectNameAtPurchase(subjectRepository.findById(subjectId).orElseThrow().getName())
                    .build()));
        });

        Payment payment = Payment.builder()
                .subscription(savedSubscription)
                .renewalSubscription(online && renewal ? current : null)
                .renewalStart(online && renewal ? renewalStart : null)
                .paymentType("Subscription")
                .amount(priceAtPurchase)
                .method(paymentMethod)
                .paymentDate(LocalDateTime.now(paymentClock).withNano(0))
                .expiresAt(online ? LocalDateTime.now(paymentClock).withNano(0).plusMinutes(VnpayService.PAYMENT_TIMEOUT_MINUTES) : null)
                .status(online ? "Pending" : "Paid")
                .discountAmount(discountAmount)
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        // Ghi nhận Audit Log lịch sử thanh toán gói hội viên
        auditLogService.recordLog(
                member,
                "PURCHASE_MEMBERSHIP",
                "Payment",
                String.format("Hội viên %s thanh toán gói '%s' số tiền %s VND (Phương thức: %s)",
                        member.getFullName(),
                        membershipPackage.getName(),
                        membershipPackage.getPrice() != null ? String.format("%,d", membershipPackage.getPrice().longValue()) : "0",
                        paymentMethod)
        );

        MemberPackageSubscriptionData data = MemberPackageSubscriptionData.fromEntities(savedSubscription, savedPayment);
        if (online && "Card".equals(paymentMethod)) data.setPaymentUrl(vnpay.createPaymentUrl(savedPayment, ipAddress));
        return new MemberPackageSubscriptionResponse(
                true,
                online ? "Đã tạo giao dịch chờ thanh toán gói hội viên" : "Đăng ký gói hội viên thành công",
                data
        );
    }

    @Transactional(readOnly = true)
    public MemberPackageSubscriptionListResponse getMySubscriptions(
            String principal,
            String status,
            int page,
            int size
    ) {
        User member = requireMember(principal);
        validatePage(page, size);
        String normalizedStatus = normalizeSubscriptionStatus(status);

        Pageable pageable = PageRequest.of(page, size);
        Page<MemberSubscription> subscriptions = subscriptionRepository.findMemberSubscriptions(
                member.getId(), normalizedStatus, pageable
        );

        List<Integer> ids = subscriptions.getContent().stream().map(MemberSubscription::getId).toList();
        Map<Integer, Payment> paymentsBySubscriptionId = new HashMap<>();
        if (!ids.isEmpty()) {
            paymentRepository.findBySubscription_IdInOrderByIdDesc(ids).forEach(payment -> {
                Integer subscriptionId = payment.getSubscription().getId();
                paymentsBySubscriptionId.putIfAbsent(subscriptionId, payment);
            });
        }

        List<MemberPackageSubscriptionData> items = subscriptions.getContent().stream()
                .map(subscription -> MemberPackageSubscriptionData.fromEntities(
                        subscription,
                        paymentsBySubscriptionId.get(subscription.getId())
                ))
                .toList();

        return new MemberPackageSubscriptionListResponse(
                true,
                "Lấy lịch sử gói hội viên thành công",
                subscriptions.getTotalElements(),
                page,
                size,
                items
        );
    }

    @Transactional(readOnly = true)
    public MemberPackageSubscriptionResponse getMySubscription(String principal, Integer subscriptionId) {
        User member = requireMember(principal);
        if (subscriptionId == null || subscriptionId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subscriptionId không hợp lệ");
        }

        MemberSubscription subscription = subscriptionRepository.findByIdAndUser_Id(subscriptionId, member.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gói hội viên này"));
        Payment payment = paymentRepository.findFirstBySubscription_IdOrderByIdDesc(subscriptionId).orElse(null);

        return new MemberPackageSubscriptionResponse(
                true,
                "Lấy thông tin gói hội viên thành công",
                MemberPackageSubscriptionData.fromEntities(subscription, payment)
        );
    }

    private User requireMember(String principal) {
        if (!StringUtils.hasText(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để thực hiện");
        }

        final Integer userId;
        try {
            userId = Integer.valueOf(principal);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản đăng nhập");
        }

        User member = userRepository.findByIdAndRoleRoleNameIgnoreCase(userId, "Member")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ hội viên mới được sử dụng API này"));
        if (!"Active".equalsIgnoreCase(member.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản hội viên không hoạt động");
        }
        return member;
    }

    private Map<Integer, List<Integer>> validateSelections(
            Integer packageId,
            List<MemberPackageRegistrationRequest.ChoiceGroupSelectionRequest> inputs
    ) {
        List<PackageChoiceGroup> groups = choiceGroupRepository.findByPackageId(packageId);
        Map<Integer, MemberPackageRegistrationRequest.ChoiceGroupSelectionRequest> inputByGroup = new HashMap<>();
        if (inputs != null) {
            for (MemberPackageRegistrationRequest.ChoiceGroupSelectionRequest input : inputs) {
                if (input == null || input.getChoiceGroupId() == null || inputByGroup.put(input.getChoiceGroupId(), input) != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choice Group selection bị trùng hoặc không hợp lệ");
                }
            }
        }
        if (inputByGroup.size() != groups.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phải chọn Sport cho đầy đủ tất cả Choice Group");
        }

        Map<Integer, List<Integer>> result = new HashMap<>();
        for (PackageChoiceGroup group : groups) {
            MemberPackageRegistrationRequest.ChoiceGroupSelectionRequest input = inputByGroup.get(group.getId());
            if (input == null || input.getSportIds() == null
                    || input.getSportIds().size() != group.getRequiredSelection()
                    || new java.util.HashSet<>(input.getSportIds()).size() != input.getSportIds().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Choice Group '" + group.getGroupName() + "' phải chọn chính xác " + group.getRequiredSelection() + " Sport");
            }
            for (Integer subjectId : input.getSportIds()) {
                if (!choiceGroupSportRepository.existsByChoiceGroup_IdAndSubject_Id(group.getId(), subjectId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sport được chọn không thuộc Choice Group");
                }
            }
            result.put(group.getId(), List.copyOf(input.getSportIds()));
        }
        return result;
    }

    private void selectionSnapshot(MemberSubscription subscription, PackageIncludedSport included) {
        includedSnapshotRepository.save(MembershipIncludedSport.builder()
                .subscription(subscription)
                .subject(included.getSubject())
                .subjectNameAtPurchase(included.getSubject().getName())
                .build());
    }

    private MemberSubscription requireSingleActiveSubscription(Integer memberId, LocalDate today) {
        List<MemberSubscription> active = subscriptionRepository.findCurrentActiveSubscriptions(memberId, today);
        if (active.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn chưa có gói hội viên đang hoạt động để gia hạn");
        }
        if (active.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tài khoản đang có nhiều gói ACTIVE; cần được quản trị viên xử lý trước");
        }
        return active.getFirst();
    }

    private PackageResponse.PackageData packageData(MembershipPackage pkg) {
        PackageResponse.PackageData base = PackageResponse.PackageData.fromEntity(pkg);
        List<PackageResponse.SportData> included = includedSportRepository.findByPackageId(pkg.getId()).stream()
                .map(item -> PackageResponse.SportData.builder().id(item.getSubject().getId())
                        .name(item.getSubject().getName()).build())
                .toList();
        List<PackageResponse.ChoiceGroupData> groups = choiceGroupRepository.findByPackageId(pkg.getId()).stream()
                .map(group -> PackageResponse.ChoiceGroupData.builder()
                        .id(group.getId()).name(group.getGroupName()).description(group.getDescription())
                        .requiredSelection(group.getRequiredSelection()).sortOrder(group.getSortOrder())
                        .sports(choiceGroupSportRepository.findByChoiceGroupId(group.getId()).stream()
                                .map(item -> PackageResponse.SportData.builder().id(item.getSubject().getId())
                                        .name(item.getSubject().getName()).build()).toList())
                        .build())
                .toList();
        return PackageResponse.PackageData.builder()
                .code(base.getCode()).id(base.getId()).name(base.getName()).description(base.getDescription())
                .price(base.getPrice()).discount(base.getDiscount()).durationDays(base.getDurationDays())
                .durationMonths(base.getDurationMonths()).includedClasses(base.getIncludedClasses())
                .benefits(base.getBenefits()).termConditions(base.getTermConditions()).status(base.getStatus())
                .statusLabel(base.getStatusLabel()).includedSports(included).choiceGroups(groups).build();
    }

    private BigDecimal defaultDiscount(MembershipPackage pkg) {
        return pkg.getDiscount() == null ? BigDecimal.ZERO : pkg.getDiscount();
    }

    private void validatePackageForPurchase(MembershipPackage pkg) {
        if (pkg.getDuration() == null || pkg.getDuration() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thời hạn gói hội viên không hợp lệ");
        }
        if (pkg.getPrice() == null || pkg.getPrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giá gói hội viên không hợp lệ");
        }
    }

    private String normalizeRenewalStart(String value) {
        String normalized = normalize(value);
        if (normalized == null || "IMMEDIATE".equalsIgnoreCase(normalized)) return "IMMEDIATE";
        if ("AFTER_CURRENT".equalsIgnoreCase(normalized)) return "AFTER_CURRENT";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "renewalStart chỉ nhận IMMEDIATE hoặc AFTER_CURRENT");
    }

    private MembershipPackage findActivePackage(Integer packageId) {
        if (packageId == null || packageId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "packageId không hợp lệ");
        }

        MembershipPackage membershipPackage = packageRepository.findById(packageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gói dịch vụ"));
        if (!"Active".equalsIgnoreCase(membershipPackage.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy gói dịch vụ đang mở bán");
        }
        return membershipPackage;
    }

    private String normalizePaymentMethod(String rawMethod) {
        if (!StringUtils.hasText(rawMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn hình thức thanh toán");
        }

        String normalized = rawMethod.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "_");
        return switch (normalized) {
            case "cash" -> "Cash";
            case "card" -> "Card";
            case "bank_transfer", "banktransfer" -> "Bank_Transfer";
            case "e_wallet", "ewallet", "wallet" -> "E_Wallet";
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "paymentMethod chỉ nhận Cash, Card, Bank_Transfer hoặc E_Wallet"
            );
        };
    }

    private String normalizeSubscriptionStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null || "all".equalsIgnoreCase(normalized)) {
            return null;
        }
        return SUBSCRIPTION_STATUSES.stream()
                .filter(validStatus -> validStatus.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "status chỉ nhận Active, Pending, Expired, Cancelled hoặc all"
                ));
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page phải lớn hơn hoặc bằng 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size phải nằm trong khoảng 1 đến 100");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
