package com.swp391.beswp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SepayWebhookRequest(Long id, String gateway, String accountNumber, String code,
                                  String content, String transferType, BigDecimal transferAmount) { }
