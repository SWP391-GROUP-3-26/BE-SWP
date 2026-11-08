package com.swp391.beswp.controller;

import com.swp391.beswp.dto.MembershipFreezeRequest;
import com.swp391.beswp.dto.MembershipFreezeResponse;
import com.swp391.beswp.service.MembershipFreezeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member/packages/subscriptions/{subscriptionId}")
@RequiredArgsConstructor
public class MembershipFreezeController {
    private final MembershipFreezeService freezes;

    @GetMapping("/freeze")
    public MembershipFreezeResponse state(Authentication auth, @PathVariable Integer subscriptionId) {
        return freezes.state(auth.getName(), subscriptionId);
    }

    @PostMapping("/freeze")
    public MembershipFreezeResponse freeze(Authentication auth, @PathVariable Integer subscriptionId,
                                           @Valid @RequestBody MembershipFreezeRequest request) {
        return freezes.freeze(auth.getName(), subscriptionId, request.months());
    }

    @PostMapping("/resume")
    public MembershipFreezeResponse resume(Authentication auth, @PathVariable Integer subscriptionId) {
        return freezes.resume(auth.getName(), subscriptionId);
    }
}
