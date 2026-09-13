package com.example.shopupu.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "payments")
public class PaymentProperties {

    @NotBlank
    @Pattern(regexp = "stub|stripe",
            message = "This demo allows only local simulation (stub) or Stripe test mode (stripe)")
    private String defaultProvider = "stub";

    @NotBlank
    @Pattern(regexp = "EUR", message = "This single-currency demo requires EUR")
    private String currency = "EUR";

    private String serviceBaseUrl;
    private String serviceClientId;
    private String serviceSecret;
    private String callbackSecret;

    @NotBlank
    private String callbackUrl;

    @NotNull
    @Min(1)
    @Max(60)
    private Integer requestTimeoutSeconds = 10;

    private Monobank monobank = new Monobank();
    private Fondy fondy = new Fondy();

    @Valid
    @NotNull
    private Stripe stripe = new Stripe();

    @Data
    public static class Stripe {
        @Pattern(regexp = "^$|^sk_test_[A-Za-z0-9]+$", message = "Only a Stripe test secret key is allowed")
        private String secretKey = "";

        @Pattern(regexp = "^$|^whsec_[A-Za-z0-9]+$", message = "Invalid Stripe test webhook signing secret")
        private String webhookSecret = "";

        @NotBlank
        private String frontendBaseUrl = "http://localhost:3000";

        public boolean isAvailable() {
            return secretKey != null && secretKey.startsWith("sk_test_")
                    && webhookSecret != null && webhookSecret.startsWith("whsec_");
        }
    }

    @Data
    public static class Monobank {
        private String apiBaseUrl = "https://api.monobank.ua";
        private String token;
    }

    @Data
    public static class Fondy {
        private String apiBaseUrl = "https://pay.fondy.eu/api";
        private String merchantId;
        private String secret;
    }
}
