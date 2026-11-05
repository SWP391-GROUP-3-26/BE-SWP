package com.swp391.beswp.controller;

import com.swp391.beswp.dto.VoucherDtos;
import com.swp391.beswp.service.VoucherApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/member/voucher-payments")
@RequiredArgsConstructor
public class MemberVoucherPaymentController {

    private final VoucherApplicationService voucherApplicationService;

    @GetMapping("/available")
    public ResponseEntity<List<VoucherDtos.VoucherOption>> available(Authentication authentication) {
        return ResponseEntity.ok(voucherApplicationService.available(authentication, "Member"));
    }

    @PostMapping("/packages")
    public ResponseEntity<VoucherDtos.VoucherCheckoutResponse> checkoutPackage(
            Authentication authentication,
            HttpServletRequest servletRequest,
            @Valid @RequestBody VoucherDtos.MemberPackageCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherApplicationService.checkoutMemberPackage(
                authentication, request, servletRequest.getRemoteAddr(), false));
    }

    @PostMapping("/packages/renewal")
    public ResponseEntity<VoucherDtos.VoucherCheckoutResponse> renewPackage(
            Authentication authentication,
            HttpServletRequest servletRequest,
            @Valid @RequestBody VoucherDtos.MemberPackageCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherApplicationService.checkoutMemberPackage(
                authentication, request, servletRequest.getRemoteAddr(), true));
    }

    @PostMapping("/classes")
    public ResponseEntity<VoucherDtos.VoucherCheckoutResponse> checkoutClass(
            Authentication authentication,
            HttpServletRequest servletRequest,
            @Valid @RequestBody VoucherDtos.MemberClassCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherApplicationService.checkoutMemberClass(
                authentication, request, servletRequest.getRemoteAddr()));
    }
}
