package com.example.shopupu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RU: Конфигурация для платёжных провайдеров.
 * EN: Configuration for payment providers.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "payments")
public class PaymentProperties {

    private String defaultProvider;
    private String currency;

    private Stripe stripe = new Stripe();
    private Paypal paypal = new Paypal();

    @Data
    public static class Stripe {
        private String apiKey;
        private String webhookSecret;
    }

    @Data
    public static class Paypal {
        private String clientId;
        private String clientSecret;
    }
}
