package com.example.shopupu.config;

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
    @Pattern(regexp = "stub|bank_back|monobank|fondy",
            message = "payments.default-provider must be one of: stub, bank_back, monobank, fondy")
    private String defaultProvider = "stub";

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "payments.currency must be a 3-letter ISO code")
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
