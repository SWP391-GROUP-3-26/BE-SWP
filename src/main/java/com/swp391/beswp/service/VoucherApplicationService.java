package com.swp391.beswp.service;

import com.swp391.beswp.dto.MemberClassBookingRequest;
import com.swp391.beswp.dto.MemberClassBookingResponse;
import com.swp391.beswp.dto.MemberPackageRegistrationRequest;
import com.swp391.beswp.dto.MemberPackageSubscriptionData;
import com.swp391.beswp.dto.MemberPackageSubscriptionResponse;
import com.swp391.beswp.dto.ReceptionistClassRegistrationRequest;
import com.swp391.beswp.dto.ReceptionistPackageRegistrationRequest;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.ClassRegistrationResponse;
import com.swp391.beswp.dto.ReceptionistRegistrationDtos.PackageRegistrationResponse;
import com.swp391.beswp.dto.SepayQrPayment;
import com.swp391.beswp.dto.VoucherDtos;
import com.swp391.beswp.entity.MemberSubscription;
import com.swp391.beswp.entity.Payment;
import com.swp391.beswp.entity.User;
import com.swp391.beswp.entity.Voucher;
import com.swp391.beswp.repository.MemberPackageSubscriptionRepository;
import com.swp391.beswp.repository.PackageChoiceGroupRepository;
import com.swp391.beswp.repository.PaymentRepository;
import com.swp391.beswp.repository.VoucherPaymentLinkRepository;
import com.swp391.beswp.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VoucherApplicationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final VoucherRepository vouchers;
    private final VoucherPaymentLinkRepository paymentVoucherLinks;
    private final PaymentRepository payments;
    private final MemberPackageSubscriptionRepository subscriptions;
    private final PackageChoiceGroupRepository packageChoiceGroups;
    private final MemberPackageService memberPackages;
    private final MemberClassBookingService memberBookings;
    private final ReceptionistRegistrationService receptionistRegistrations;
    private final AuditLogService auditLogService;
    private final VnpayService vnpay;
    private final SepayService sepay;
    private final Clock paymentClock;

    @Transactional(readOnly = true)
    public List<VoucherDtos.VoucherOption> available(Authentication authentication, String requiredRole) {
        requireRole(authentication, requiredRole);
        return vouchers.findAvailableOn(today()).stream().map(this::voucherOption).toList();
    }

    @Transactional
    public VoucherDtos.VoucherCheckoutResponse checkoutMemberPackage(
            Authentication authentication,
            VoucherDtos.MemberPackageCheckoutRequest request,
            String ipAddress,
            boolean renewal) {
        User member = requireRole(authentication, "Member");
        MemberPackageRegistrationRequest purchase = request.purchase();
        MemberPackageSubscriptionResponse result = memberPackages.registerOnlinePackage(
                authentication.getName(), purchase, ipAddress, renewal);
        MemberPackageSubscriptionData data = result.getData();
        if (data == null || data.getPaymentId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy giao dịch gói hội viên vừa tạo");
        }
        Payment payment = ownedPayment(data.getPaymentId(), member.getId());
        AppliedVoucher applied = applyVoucher(payment, request.voucherCode(), false);
        String paymentUrl = paymentUrl(payment, ipAddress);
        SepayQrPayment qr = qrPayment(payment);
        data.setPaymentUrl(paymentUrl);
        return checkoutResponse("Đã áp dụng voucher cho thanh toán gói hội viên", member.getId(),
                "Subscription", purchase.getPackageId(), data.getSubscriptionId(), null, payment,
                applied, paymentUrl, qr);
    }

    @Transactional
    public VoucherDtos.VoucherCheckoutResponse checkoutMemberClass(
            Authentication authentication,
            VoucherDtos.MemberClassCheckoutRequest request,
            String ipAddress) {
        User member = requireRole(authentication, "Member");
        MemberClassBookingRequest purchase = request.purchase();
        requireMemberOnlineClassPayment(purchase);
        MemberClassBookingResponse result = memberBookings.registerClass(
                authentication.getName(), purchase.getClassId(), purchase, ipAddress);
        if (result.getPaymentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Voucher chỉ áp dụng cho thanh toán trực tiếp, không áp dụng khi dùng toàn bộ lượt gói hội viên");
        }
        Payment payment = ownedPayment(result.getPaymentId(), member.getId());
        AppliedVoucher applied = applyVoucher(payment, request.voucherCode(), false);
        String paymentUrl = paymentUrl(payment, ipAddress);
        SepayQrPayment qr = qrPayment(payment);
        result.setPaymentAmount(payment.getAmount());
        result.setPaymentUrl(paymentUrl);
        return checkoutResponse("Đã áp dụng voucher cho thanh toán lớp học", member.getId(),
                "Class_Booking", purchase.getClassId(), null, result.getBookingId(), payment,
                applied, paymentUrl, qr);
    }

    @Transactional
    public VoucherDtos.VoucherCheckoutResponse checkoutReceptionistPackage(
            Authentication authentication,
            Integer memberId,
            VoucherDtos.ReceptionistPackageCheckoutRequest request,
            String ipAddress,
            boolean renewal) {
        requireRole(authentication, "Receptionist");
        ReceptionistPackageRegistrationRequest purchase = request.purchase();
        String channel = requireReceptionistPackageChannel(purchase.paymentMethod());
        if (!renewal && !packageChoiceGroups.findByPackageId(purchase.packageId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Gói có lựa chọn môn thể thao hiện chưa hỗ trợ đăng ký mới qua luồng lễ tân");
        }
        Object result;
        if (renewal) {
            result = receptionistRegistrations.renewPackage(authentication, memberId, purchase, ipAddress);
        } else if ("Cash".equals(channel)) {
            result = receptionistRegistrations.registerPackage(authentication, memberId, purchase);
        } else {
            result = receptionistRegistrations.registerOnlinePackage(authentication, memberId, purchase, ipAddress);
        }

        Integer paymentId;
        Integer subscriptionId = null;
        if (result instanceof PackageRegistrationResponse packageResult) {
            paymentId = packageResult.paymentId();
            subscriptionId = packageResult.subscriptionId();
        } else if (result instanceof MemberPackageSubscriptionResponse subscriptionResult
                && subscriptionResult.getData() != null) {
            paymentId = subscriptionResult.getData().getPaymentId();
            subscriptionId = subscriptionResult.getData().getSubscriptionId();
        } else if (result instanceof java.util.Map<?, ?> responseMap) {
            paymentId = integerValue(responseMap.get("paymentId"));
        } else {
            paymentId = null;
        }
        if (paymentId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy giao dịch gói hội viên vừa tạo");
        }

        Payment payment = ownedPayment(paymentId, memberId);
        if (subscriptionId == null && payment.getSubscription() != null) {
            subscriptionId = payment.getSubscription().getId();
        }
        AppliedVoucher applied = applyVoucher(payment, request.voucherCode(), "Cash".equals(channel));
        String paymentUrl = paymentUrl(payment, ipAddress);
        SepayQrPayment qr = qrPayment(payment);
        return checkoutResponse("Đã áp dụng voucher cho thanh toán tại quầy", memberId,
                "Subscription", purchase.packageId(), subscriptionId, null, payment, applied,
                paymentUrl, qr);
    }

    @Transactional
    public VoucherDtos.VoucherCheckoutResponse checkoutReceptionistClass(
            Authentication authentication,
            Integer memberId,
            VoucherDtos.ReceptionistClassCheckoutRequest request,
            String ipAddress) {
        requireRole(authentication, "Receptionist");
        ReceptionistClassRegistrationRequest purchase = request.purchase();
        String channel = requireReceptionistClassChannel(purchase);
        Object result = "Cash".equals(channel)
                ? receptionistRegistrations.registerClass(authentication, memberId, purchase)
                : receptionistRegistrations.registerOnlineClass(authentication, memberId, purchase, ipAddress);

        Integer paymentId;
        Integer bookingId;
        if (result instanceof ClassRegistrationResponse classResult) {
            paymentId = classResult.paymentId();
            bookingId = classResult.bookingId();
        } else if (result instanceof MemberClassBookingResponse bookingResult) {
            paymentId = bookingResult.getPaymentId();
            bookingId = bookingResult.getBookingId();
        } else {
            paymentId = null;
            bookingId = null;
        }
        if (paymentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Voucher chỉ áp dụng cho đăng ký lớp thanh toán trực tiếp, không áp dụng khi trừ gói hội viên");
        }

        Payment payment = ownedPayment(paymentId, memberId);
        AppliedVoucher applied = applyVoucher(payment, request.voucherCode(), "Cash".equals(channel));
        String paymentUrl = paymentUrl(payment, ipAddress);
        SepayQrPayment qr = qrPayment(payment);
        if (result instanceof MemberClassBookingResponse bookingResult) {
            bookingResult.setPaymentAmount(payment.getAmount());
            bookingResult.setPaymentUrl(paymentUrl);
        }
        return checkoutResponse("Đã áp dụng voucher cho thanh toán lớp học tại quầy", memberId,
                "Class_Booking", purchase.classId(), null, bookingId, payment, applied,
                paymentUrl, qr);
    }

    private AppliedVoucher applyVoucher(Payment payment, String rawCode, boolean allowCashZero) {
        requirePayablePayment(payment, allowCashZero);
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch không có số tiền có thể áp dụng voucher");
        }
        Voucher voucher = findValidVoucher(rawCode);
        BigDecimal before = payment.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = calculateDiscount(voucher, before);
        BigDecimal after = before.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        boolean online = "Pending".equals(payment.getStatus());
        if (online && (after.signum() <= 0 || after.stripTrailingZeros().scale() > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số tiền sau voucher phải là số VND nguyên dương để thanh toán trực tuyến");
        }
        if (after.signum() < 0 || after.precision() - after.scale() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền sau voucher không hợp lệ");
        }
        if (paymentVoucherLinks.attachVoucher(payment.getId(), voucher.getId()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch đã được áp dụng voucher hoặc không còn khả dụng");
        }

        BigDecimal totalDiscount = (payment.getDiscountAmount() == null ? BigDecimal.ZERO : payment.getDiscountAmount())
                .add(discount).setScale(2, RoundingMode.HALF_UP);
        payment.setAmount(after);
        payment.setDiscountAmount(totalDiscount);
        payments.save(payment);
        if (payment.getSubscription() != null) {
            MemberSubscription subscription = payment.getSubscription();
            subscription.setPriceAtPurchase(after);
            if (subscription.getDurationAtPurchase() == null && subscription.getMembershipPackage() != null) {
                subscription.setDurationAtPurchase(subscription.getMembershipPackage().getDuration());
            }
            if (!StringUtils.hasText(subscription.getPackageNameAtPurchase())
                    && subscription.getMembershipPackage() != null) {
                subscription.setPackageNameAtPurchase(subscription.getMembershipPackage().getName());
            }
            if (subscription.getTermsAtPurchase() == null && subscription.getMembershipPackage() != null) {
                subscription.setTermsAtPurchase(subscription.getMembershipPackage().getTermConditions());
            }
            subscriptions.save(subscription);
        }
        User actor = payment.getReceptionist() != null ? payment.getReceptionist()
                : payment.getSubscription() != null ? payment.getSubscription().getUser()
                : payment.getBooking() != null ? payment.getBooking().getUser() : null;
        auditLogService.recordLog(actor, "APPLY_VOUCHER", "Payment",
                String.format("Áp dụng voucher %s cho thanh toán #%d: giảm %s VND, số tiền phải trả %s VND",
                        voucher.getCode(), payment.getId(), discount.toPlainString(), after.toPlainString()));
        return new AppliedVoucher(voucher, before, discount, after, totalDiscount);
    }

    private void requirePayablePayment(Payment payment, boolean allowCashZero) {
        if (payment.getId() == null || paymentVoucherLinks.hasVoucher(payment.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch đã được áp dụng voucher hoặc không hợp lệ");
        }
        if ("Pending".equals(payment.getStatus())) {
            requirePendingPayment(payment);
            requireOnlinePaymentChannel(payment);
            return;
        }
        if (allowCashZero && "Paid".equals(payment.getStatus()) && "Cash".equals(payment.getMethod())
                && payment.getReceptionist() != null) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể áp dụng voucher trong lúc tạo thanh toán");
    }

    private void requirePendingPayment(Payment payment) {
        LocalDateTime now = LocalDateTime.now(paymentClock).withNano(0);
        if (!"Pending".equals(payment.getStatus()) || payment.isRequiresReview()
                || payment.getExpiresAt() == null || !payment.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giao dịch không còn ở trạng thái chờ thanh toán");
        }
    }

    private void requireOnlinePaymentChannel(Payment payment) {
        if (!List.of("Card", "E_Wallet").contains(payment.getMethod())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kênh thanh toán online không hỗ trợ voucher");
        }
    }

    private Voucher findValidVoucher(String rawCode) {
        if (!StringUtils.hasText(rawCode) || rawCode.trim().length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã voucher không hợp lệ");
        }
        Voucher voucher = vouchers.findByCodeIgnoreCase(rawCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy voucher"));
        LocalDate today = today();
        if (today.isBefore(voucher.getStartDate()) || today.isAfter(voucher.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher chưa bắt đầu hoặc đã hết hạn");
        }
        if (voucher.getDiscountValue() == null || voucher.getDiscountValue().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Giá trị voucher không hợp lệ");
        }
        if ("Percent".equalsIgnoreCase(voucher.getDiscountType())) {
            if (voucher.getDiscountValue().compareTo(ONE_HUNDRED) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phần trăm voucher vượt quá 100%");
            }
        } else if (!"Fixed".equalsIgnoreCase(voucher.getDiscountType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loại voucher không được hỗ trợ");
        }
        return voucher;
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal amount) {
        BigDecimal discount = "Percent".equalsIgnoreCase(voucher.getDiscountType())
                ? amount.multiply(voucher.getDiscountValue()).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
                : voucher.getDiscountValue().min(amount);
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private Payment ownedPayment(Integer paymentId, Integer memberId) {
        if (paymentId == null || paymentId <= 0 || memberId == null || memberId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã thanh toán hoặc hội viên không hợp lệ");
        }
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thanh toán"));
        Integer owner = payment.getSubscription() != null && payment.getSubscription().getUser() != null
                ? payment.getSubscription().getUser().getId()
                : payment.getBooking() != null && payment.getBooking().getUser() != null
                ? payment.getBooking().getUser().getId() : null;
        if (!Objects.equals(owner, memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thanh toán của hội viên");
        }
        return payment;
    }

    private User requireRole(Authentication authentication, String role) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a ->
                ("ROLE_" + role).equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền sử dụng API voucher này");
        }
        if ("Member".equals(role)) return memberBookings.findMemberUser(authentication.getName());
        return null;
    }

    private void requireMemberOnlineClassPayment(MemberClassBookingRequest purchase) {
        String method = purchase.getPaymentMethod() == null ? "" : purchase.getPaymentMethod().trim();
        String channel = purchase.getPaymentChannel() == null ? "" : purchase.getPaymentChannel().trim();
        if (!("DIRECT".equalsIgnoreCase(method) || "MIXED".equalsIgnoreCase(method))
                || !("Card".equalsIgnoreCase(channel) || "E_Wallet".equalsIgnoreCase(channel))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thanh toán lớp có voucher chỉ hỗ trợ VNPay hoặc QR SePay; không áp dụng cho lượt PACKAGE");
        }
    }

    private String requireReceptionistPackageChannel(String raw) {
        String channel = normalizedChannel(raw);
        if (!List.of("Cash", "Card", "E_Wallet").contains(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kênh thanh toán gói không hợp lệ");
        }
        return channel;
    }

    private String requireReceptionistClassChannel(ReceptionistClassRegistrationRequest purchase) {
        if (!"DIRECT".equalsIgnoreCase(purchase.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Voucher không áp dụng cho đăng ký lớp bằng lượt gói hội viên");
        }
        String channel = normalizedChannel(purchase.paymentChannel());
        if (!List.of("Cash", "Card", "E_Wallet").contains(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kênh thanh toán lớp không hợp lệ");
        }
        return channel;
    }

    private String normalizedChannel(String raw) {
        if (!StringUtils.hasText(raw)) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("cash")) return "Cash";
        if (value.equals("card")) return "Card";
        if (value.equals("e_wallet") || value.equals("ewallet")) return "E_Wallet";
        return "";
    }

    private String paymentUrl(Payment payment, String ipAddress) {
        if (!"Pending".equals(payment.getStatus()) || !"Card".equals(payment.getMethod())) return null;
        return vnpay.createPaymentUrl(payment, ipAddress);
    }

    private SepayQrPayment qrPayment(Payment payment) {
        if (!"Pending".equals(payment.getStatus()) || !"E_Wallet".equals(payment.getMethod())) return null;
        return sepay.qrPayment(payment);
    }

    private VoucherDtos.VoucherCheckoutResponse checkoutResponse(
            String message, Integer memberId, String targetType, Integer targetId,
            Integer subscriptionId, Integer bookingId, Payment payment, AppliedVoucher applied,
            String paymentUrl, SepayQrPayment qr) {
        return VoucherDtos.success(message, memberId, targetType, targetId, subscriptionId, bookingId,
                payment.getId(), payment.getStatus(), payment.getMethod(), voucherOption(applied.voucher()),
                applied.amountBefore(), applied.voucherDiscount(), applied.totalDiscount(),
                payment.getAmount(), paymentUrl, qr);
    }

    private VoucherDtos.VoucherOption voucherOption(Voucher voucher) {
        return new VoucherDtos.VoucherOption(voucher.getId(), voucher.getCode(), voucher.getDiscountType(),
                voucher.getDiscountValue(), voucher.getStartDate(), voucher.getEndDate());
    }

    private LocalDate today() {
        return LocalDate.now(paymentClock);
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private record AppliedVoucher(Voucher voucher, BigDecimal amountBefore, BigDecimal voucherDiscount,
                                  BigDecimal amountAfter, BigDecimal totalDiscount) {
    }
}
