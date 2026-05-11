package com.example.shopupu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Data
@Configuration
@ConfigurationProperties(prefix = "payments")
/**
 * describes the PaymentProperties class.
 */
public class PaymentProperties {

    private String defaultProvider = "stub";
    private String currency = "EUR";
    private String serviceBaseUrl;
    private String serviceClientId;
    private String serviceSecret;
    private String callbackSecret;
    private String callbackUrl;
    private Integer requestTimeoutSeconds = 10;
}
