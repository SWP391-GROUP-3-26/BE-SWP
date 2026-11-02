package com.swp391.beswp.service;

import com.swp391.beswp.dto.RefundDtos.*;
import com.swp391.beswp.entity.*;
import com.swp391.beswp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class PaymentHistoryService {
    private final PaymentRepository payments;
    private final RefundRequestRepository requests;
    private final RefundService refunds;

    public PaymentPage list(Authentication auth, String status, String type, LocalDate from, LocalDate to, int page, int size) {
        User member = refunds.actor(auth, "Member");
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))
                || (status != null && !Set.of("Pending", "Paid", "Failed").contains(status))
                || (type != null && !Set.of("Class_Booking", "Subscription").contains(type))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc thanh toán không hợp lệ");
        }
        var result = payments.findMemberHistory(member.getId(), status, type, from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(), PageRequest.of(page, size));
        Map<Integer, RefundRequest> byPayment = result.isEmpty() ? Map.of() : requests.findByPayment_IdIn(result.getContent().stream().map(Payment::getId).toList())
                .stream().collect(Collectors.toMap(r -> r.getPayment().getId(), Function.identity()));
        return new PaymentPage(result.getContent().stream().map(p -> view(p, byPayment.get(p.getId()), false)).toList(), page, size, result.getTotalElements());
    }

    public PaymentView detail(Authentication auth, Integer id) {
        User member = refunds.actor(auth, "Member");
        Payment p = payments.findById(id).orElseThrow(this::notFound);
        User owner = p.getBooking() != null ? p.getBooking().getUser() : p.getSubscription() != null ? p.getSubscription().getUser() : null;
        if (owner == null || !member.getId().equals(owner.getId())) throw notFound();
        return view(p, requests.findByPayment_Id(id).orElse(null), true);
    }

    private PaymentView view(Payment p, RefundRequest r, boolean detail) {
        String item = p.getItemNameAtPayment();
        if (item == null) item = p.getBooking() != null ? p.getBooking().getSession().getClassEntity().getName()
                : p.getSubscription().getPackageNameAtPurchase() != null ? p.getSubscription().getPackageNameAtPurchase()
                : p.getSubscription().getMembershipPackage().getName();
        BigDecimal returned = r != null && "PAID".equals(r.getStatus()) ? r.getApprovedAmount() : BigDecimal.ZERO;
        String reference = p.getVnpTransactionNo() != null ? p.getVnpTransactionNo() : p.getSepayTransactionId() != null ? p.getSepayTransactionId().toString() : null;
        return new PaymentView(p.getId(), item, p.getPaymentType(), p.getAmount(), p.getDiscountAmount(), p.getAmount().add(p.getDiscountAmount()),
                p.getMethod(), p.getStatus(), p.getCreatedAt() != null ? p.getCreatedAt() : p.getPaymentDate(), p.getPaidAt(), p.isRequiresReview(),
                reference, returned, "Paid".equals(p.getStatus()) ? p.getAmount().subtract(returned) : BigDecimal.ZERO, r == null ? null : refunds.view(r, detail, detail));
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thanh toán của bạn"); }
}
