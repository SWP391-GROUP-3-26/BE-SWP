package com.swp391.beswp.service;

import com.swp391.beswp.config.VnpayConfig;
import com.swp391.beswp.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VnpayService {
    public static final ZoneOffset OFFSET = ZoneOffset.ofHours(7);
    public static final int PAYMENT_TIMEOUT_MINUTES = 15;
    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final VnpayConfig config;

    public void requireConfigured() {
        if (!StringUtils.hasText(config.getTmnCode()) || !StringUtils.hasText(config.getHashSecret())
                || !validUrl(config.getReturnUrl()) || !validUrl(config.getFrontendUrl())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "VNPay chưa được cấu hình hợp lệ: VNPAY_TMN_CODE, VNPAY_HASH_SECRET, VNPAY_RETURN_URL, VNPAY_FRONTEND_URL");
        }
    }

    private boolean validUrl(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            URI uri = URI.create(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String createPaymentUrl(Payment payment, String ipAddress) {
        requireConfigured();
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", config.getTmnCode());
        params.put("vnp_Amount", payment.getAmount().movePointRight(2).toBigIntegerExact().toString());
        params.put("vnp_BankCode", "VNBANK");
        params.put("vnp_CreateDate", DATE.format(payment.getPaymentDate()));
        params.put("vnp_ExpireDate", DATE.format(payment.getExpiresAt()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_Locale", "vn");
        params.put("vnp_OrderInfo", "Thanh toan lop hoc " + payment.getId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_ReturnUrl", config.getReturnUrl());
        params.put("vnp_TxnRef", payment.getId().toString());
        String query = canonicalize(params);
        return PAY_URL + "?" + query + "&vnp_SecureHash=" + sign(params);
    }

    public boolean validSignature(Map<String, String> params) {
        requireConfigured();
        String received = params.get("vnp_SecureHash");
        if (received == null || !received.matches("(?i)[0-9a-f]{128}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(received), HexFormat.of().parseHex(sign(params)));
    }

    public boolean validMerchant(Map<String, String> params) {
        return config.getTmnCode().equals(params.get("vnp_TmnCode"));
    }

    public String frontendResultUrl(Integer paymentId) {
        requireConfigured();
        return config.getFrontendUrl().replaceAll("/+$", "") + "/member/payment-result?paymentId=" + paymentId;
    }

    // VNPay 2.1.0 signs sorted, form-encoded nonempty values, excluding the checksum fields.
    String sign(Map<String, String> params) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(config.getHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonicalize(params).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Không thể tạo chữ ký VNPay", e);
        }
    }

    private String canonicalize(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_") && !e.getKey().equals("vnp_SecureHash")
                        && !e.getKey().equals("vnp_SecureHashType") && e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
