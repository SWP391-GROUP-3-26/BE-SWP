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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/receptionist/voucher-payments")
@RequiredArgsConstructor
public class ReceptionistVoucherPaymentController {

    private final VoucherApplicationService voucherApplicationService;

    @GetMapping("/available")
    public ResponseEntity<List<VoucherDtos.VoucherOption>> available(Authentication authentication) {
        return ResponseEntity.ok(voucherApplicationService.available(authentication, "Receptionist"));
    }

    @PostMapping("/members/{memberId}/packages")
    public ResponseEntity<VoucherDtos.VoucherCheckoutResponse> checkoutPackage(
            Authentication authentication,
            @PathVariable Integer memberId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody VoucherDtos.ReceptionistPackageCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherApplicationService.checkoutReceptionistPackage(
                authentication, memberId, request, servletRequest.getRemoteAddr(), false));
    }

    @PostMapping("/members/{memberId}/packages/renewal")
    public ResponseEntity<VoucherDtos.VoucherCheckoutResponse> renewPackage(
            Authentication authentication,
            @PathVariable Integer memberId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody VoucherDtos.ReceptionistPackageCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherApplicationService.checkoutReceptionistPackage(
                authentication, memberId, request, servletRequest.getRemoteAddr(), true));
    }

    @PostMapping("/members/{memberId}/classes")
    public ResponseEntity<VoucherDtos.VoucherCheckoutResponse> checkoutClass(
            Authentication authentication,
            @PathVariable Integer memberId,
            HttpServletRequest servletRequest,
            @Valid @RequestBody VoucherDtos.ReceptionistClassCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherApplicationService.checkoutReceptionistClass(
                authentication, memberId, request, servletRequest.getRemoteAddr()));
    }
}
