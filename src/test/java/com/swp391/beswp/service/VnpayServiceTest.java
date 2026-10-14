package com.swp391.beswp.service;

import com.swp391.beswp.config.VnpayConfig;
import com.swp391.beswp.entity.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VnpayServiceTest {
    static VnpayService configured() {
        VnpayConfig config = new VnpayConfig();
        config.setTmnCode("TESTCODE");
        config.setHashSecret("test-secret");
        config.setReturnUrl("http://localhost:8080/api/payments/vnpay/return");
        config.setFrontendUrl("http://localhost:5173");
        return new VnpayService(config);
    }

    @Test
    void signatureMatchesIndependentVectorAndIgnoresChecksumFields() {
        VnpayService service = configured();
        Map<String, String> params = new HashMap<>(Map.of("vnp_TxnRef", "105", "vnp_TmnCode", "TESTCODE",
                "vnp_OrderInfo", "Thanh toan lop 105", "vnp_Amount", "10000000"));
        String expected = "3e1d411a68fbe56aed0a9659af09b45d9225e3ff16cb74aa443908ad05794ecffb14e79c4bfd986870daca8f8a1a83c5c827cb0807ab237328e5dc01f0b4df26";
        assertEquals(expected, service.sign(params));
        params.put("vnp_SecureHash", expected.toUpperCase());
        params.put("vnp_SecureHashType", "HmacSHA512");
        params.put("untrusted", "ignored");
        assertTrue(service.validSignature(params));
        params.put("vnp_Amount", "1");
        assertFalse(service.validSignature(params));
        params.put("vnp_SecureHash", "bad");
        assertFalse(service.validSignature(params));
    }

    @Test
    void urlUsesSandboxDomesticCardAmountTimes100AndGmt7Dates() {
        VnpayService service = configured();
        Payment payment = Payment.builder().id(105).amount(new BigDecimal("100000.00"))
                .paymentDate(LocalDateTime.of(2026, 10, 13, 8, 0)).expiresAt(LocalDateTime.of(2026, 10, 13, 8, 15)).build();
        URI url = URI.create(service.createPaymentUrl(payment, "127.0.0.1"));
        assertEquals("sandbox.vnpayment.vn", url.getHost());
        Map<String, String> params = new HashMap<>();
        for (String pair : url.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        assertEquals("VNBANK", params.get("vnp_BankCode"));
        assertEquals("2.1.0", params.get("vnp_Version"));
        assertEquals("10000000", params.get("vnp_Amount"));
        assertEquals("105", params.get("vnp_TxnRef"));
        assertEquals("20261013081500", params.get("vnp_ExpireDate"));
        assertTrue(service.validSignature(params));
        assertEquals("http://localhost:5173/member/payment-result?paymentId=105", service.frontendResultUrl(105));
    }

    @Test
    void paymentClockUsesHoChiMinhZone() {
        assertEquals(java.time.ZoneId.of("Asia/Ho_Chi_Minh"), new VnpayConfig().paymentClock().getZone());
    }

    @Test
    void missingConfigurationFailsClearly() {
        var error = assertThrows(ResponseStatusException.class, () -> new VnpayService(new VnpayConfig()).requireConfigured());
        assertEquals(503, error.getStatusCode().value());
        assertTrue(error.getReason().contains("VNPAY_HASH_SECRET"));
    }
}
