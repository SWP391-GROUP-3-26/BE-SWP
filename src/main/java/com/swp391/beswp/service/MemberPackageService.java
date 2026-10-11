package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberPackageCatalogResponse;
import com.swp391.beswp.dto.MemberPackageRegistrationRequest;
import com.swp391.beswp.dto.MemberPackageSubscriptionData;
import com.swp391.beswp.dto.MemberPackageSubscriptionListResponse;
import com.swp391.beswp.dto.MemberPackageSubscriptionResponse;
import com.swp391.beswp.dto.PackageResponse;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.MembershipPackage;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.repository.MemberPackagePaymentRepository;
import com.swp391.beswp.repository.MemberPackageSubscriptionRepository;
import com.swp391.beswp.repository.MembershipPackageRepository;
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
        return PackageResponse.success("Lấy thông tin gói dịch vụ thành công", membershipPackage);
    }

    @Transactional
    public MemberPackageSubscriptionResponse registerPackage(
            String principal,
            MemberPackageRegistrationRequest request
    ) {
        User member = requireMember(principal);
        MembershipPackage membershipPackage = findActivePackage(request.getPackageId());
        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        LocalDate today = LocalDate.now();

        if (subscriptionRepository.hasActiveSubscription(member.getId(), today)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bạn đang có gói hội viên hoạt động. Vui lòng đợi gói hiện tại hết hạn trước khi đăng ký gói mới"
            );
        }

        if (membershipPackage.getDuration() == null || membershipPackage.getDuration() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thời hạn gói hội viên không hợp lệ");
        }

        LocalDate endDate = today.plusDays(membershipPackage.getDuration().longValue() - 1L);
        MemberSubscription subscription = MemberSubscription.builder()
                .user(member)
                .membershipPackage(membershipPackage)
                .startDate(today)
                .endDate(endDate)
                .status("Active")
                .build();
        MemberSubscription savedSubscription = subscriptionRepository.save(subscription);

        Payment payment = Payment.builder()
                .subscription(savedSubscription)
                .paymentType("Subscription")
                .amount(membershipPackage.getPrice())
                .method(paymentMethod)
                .paymentDate(LocalDateTime.now())
                .status("Paid")
                .discountAmount(BigDecimal.ZERO)
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        return new MemberPackageSubscriptionResponse(
                true,
                "Đăng ký gói hội viên thành công",
                MemberPackageSubscriptionData.fromEntities(savedSubscription, savedPayment)
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
