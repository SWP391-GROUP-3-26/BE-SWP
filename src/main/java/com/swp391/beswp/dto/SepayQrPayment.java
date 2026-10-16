package com.swp391.beswp.dto;

public record SepayQrPayment(String qrUrl, String bankCode, String accountNumber, String accountName,
                             String paymentCode, boolean testMode) { }
