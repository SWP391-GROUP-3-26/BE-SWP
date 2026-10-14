package com.swp391.beswp.controller;

import com.swp391.beswp.service.MemberPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/vnpay")
public class VnpayController {
    private final MemberPaymentService payments;

    @GetMapping("/ipn")
    public Map<String, String> ipn(@RequestParam MultiValueMap<String, String> params) {
        try {
            return payments.processIpn(singleValues(params));
        } catch (ResponseStatusException e) {
            return Map.of("RspCode", "99", "Message", "Invalid request or configuration");
        } catch (RuntimeException e) {
            // Transaction failures reach here after rollback; VNPay will retry code 99.
            return Map.of("RspCode", "99", "Message", "Unknown error");
        }
    }

    @GetMapping("/return")
    public ResponseEntity<Void> returnPayment(@RequestParam MultiValueMap<String, String> params) {
        String url = payments.returnUrl(singleValues(params));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private Map<String, String> singleValues(MultiValueMap<String, String> params) {
        Map<String, String> values = new HashMap<>();
        params.forEach((key, entries) -> {
            if (key.startsWith("vnp_")) {
                if (entries.size() != 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tham số VNPay trùng lặp");
                }
                values.put(key, entries.getFirst());
            }
        });
        return values;
    }
}
