package com.swp391.beswp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.sepay")
public class SepayConfig {
    private boolean enabled;
    private boolean testMode = true;
    private String bankCode;
    private String accountNumber;
    private String accountName;
    private String webhookApiKey;
}
