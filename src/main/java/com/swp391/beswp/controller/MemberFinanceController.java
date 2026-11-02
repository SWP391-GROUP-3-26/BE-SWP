package com.swp391.beswp.controller;

import com.swp391.beswp.dto.RefundDtos.*;
import com.swp391.beswp.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequestMapping("/api/member") @RequiredArgsConstructor
public class MemberFinanceController {
    private final PaymentHistoryService history;
    private final RefundService refunds;
    @GetMapping("/payment-history")
    public Result<PaymentPage> list(Authentication auth, @RequestParam(required = false) String status,
            @RequestParam(required = false) String type, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(history.list(auth, status, type, from, to, page, size));
    }
    @GetMapping("/payment-history/{id}")
    public Result<PaymentView> detail(Authentication auth, @PathVariable Integer id) { return Result.ok(history.detail(auth, id)); }
    @GetMapping("/refunds/notifications")
    public Result<List<RefundView>> notices(Authentication auth) { return Result.ok(refunds.notifications(auth)); }
    @PutMapping("/refunds/{id}/recipient")
    public Result<RefundView> recipient(Authentication auth, @PathVariable Integer id, @Valid @RequestBody Recipient input) {
        return Result.ok(refunds.recipient(auth, id, input));
    }
    @PostMapping("/refunds/{id}/read")
    public Result<RefundView> read(Authentication auth, @PathVariable Integer id, @RequestParam int revision) {
        return Result.ok(refunds.markRead(auth, id, revision));
    }
}
