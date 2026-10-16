package com.swp391.beswp.controller;

import com.swp391.beswp.dto.SepayWebhookRequest;
import com.swp391.beswp.service.SepayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/sepay")
public class SepayController {
    private final SepayService sepay;

    @PostMapping("/webhook")
    public Map<String, Boolean> webhook(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody SepayWebhookRequest request) {
        sepay.processWebhook(authorization, request);
        return Map.of("success", true);
    }
}
