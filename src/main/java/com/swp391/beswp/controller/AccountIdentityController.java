package com.swp391.beswp.controller;

import com.swp391.beswp.service.AccountIdentityService;
import com.swp391.beswp.dto.EmailOtpDtos.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/account-identities") @RequiredArgsConstructor
public class AccountIdentityController {
    private final AccountIdentityService identities;
    public record ResolveRequest(@NotBlank @Pattern(regexp = "LOCAL|GOOGLE") String provider,
                                  @NotBlank @Size(max = 500) String reason) { }
    @GetMapping
    public Result<Page<AccountIdentityService.Identity>> unresolved(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok("Tài khoản cần rà soát nguồn đăng ký", identities.unresolved(page, size));
    }
    @PutMapping("/{id}")
    public Result<AccountIdentityService.Identity> resolve(@PathVariable Integer id, @Valid @RequestBody ResolveRequest request) {
        return Result.ok("Đã xác định nguồn tài khoản", identities.resolve(id, request.provider(), request.reason()));
    }
}
