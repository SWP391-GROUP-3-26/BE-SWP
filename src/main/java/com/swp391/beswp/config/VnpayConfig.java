package com.swp391.beswp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.vnpay")
public class VnpayConfig {
    private String tmnCode;
    private String hashSecret;
    private String returnUrl;
    private String frontendUrl;

    @Bean
    public Clock paymentClock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
