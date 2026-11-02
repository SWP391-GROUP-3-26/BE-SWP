package com.swp391.beswp.controller;

import com.swp391.beswp.dto.RefundDtos.*;
import com.swp391.beswp.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class RefundManagementController {
    private final RefundService refunds;
    @GetMapping("/api/center-manager/refunds")
    public Result<StaffQueue> managerList(Authentication auth) { return Result.ok(refunds.queue(auth, true)); }
    @GetMapping("/api/receptionist/refunds")
    public Result<StaffQueue> receptionistList(Authentication auth) { return Result.ok(refunds.queue(auth, false)); }
    @GetMapping("/api/center-manager/refunds/{id}")
    public Result<RefundView> managerDetail(Authentication auth, @PathVariable Integer id) { return Result.ok(refunds.detail(auth, id, "Center Manager")); }
    @GetMapping("/api/receptionist/refunds/{id}")
    public Result<RefundView> receptionistDetail(Authentication auth, @PathVariable Integer id) { return Result.ok(refunds.detail(auth, id, "Receptionist")); }
    @PostMapping("/api/center-manager/refunds/payments/{paymentId}")
    public Result<RefundView> legacy(Authentication auth, @PathVariable Integer paymentId) { return Result.ok(refunds.createLegacy(auth, paymentId)); }
    @PatchMapping("/api/center-manager/refunds/{id}/review")
    public Result<RefundView> review(Authentication auth, @PathVariable Integer id, @Valid @RequestBody Review input) { return Result.ok(refunds.review(auth, id, input)); }
    @PostMapping("/api/receptionist/refunds/{id}/payout")
    public Result<RefundView> payout(Authentication auth, @PathVariable Integer id, @Valid @RequestBody Payout input) { return Result.ok(refunds.payout(auth, id, input)); }
}
