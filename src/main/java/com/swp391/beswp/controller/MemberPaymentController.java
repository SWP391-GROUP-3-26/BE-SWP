package com.swp391.beswp.controller;

import com.swp391.beswp.dto.MemberPaymentResponse;
import com.swp391.beswp.service.MemberPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/member/payments")
public class MemberPaymentController {
    private final MemberPaymentService payments;

    @GetMapping("/{paymentId}")
    public MemberPaymentResponse getPayment(Authentication authentication, @PathVariable Integer paymentId) {
        return payments.getPayment(authentication.getName(), paymentId);
    }
}
