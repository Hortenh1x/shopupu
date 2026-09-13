package com.example.shopupu.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "notifications")
public class NotificationProperties {
    public enum Provider { DISABLED, SMTP, RESEND }
    @NotNull private Provider provider = Provider.DISABLED;
    @Min(1) @Max(3) private int maxAttempts = 3;
    private Resend resend = new Resend();
    private String from = "";
    @Data public static class Resend {
        private String apiKey = "";
        private String from = "";
        private long timeoutSeconds = 10;
    }
    @AssertTrue(message = "notifications requires sender address, Resend API key and timeout 1..15 seconds for the selected provider")
    public boolean isProviderConfigurationValid() {
        if (provider == Provider.DISABLED) return true;
        if (provider == Provider.SMTP) return from != null && !from.isBlank();
        return resend != null && resend.apiKey != null && !resend.apiKey.isBlank()
                && resend.from != null && !resend.from.isBlank() && resend.timeoutSeconds >= 1 && resend.timeoutSeconds <= 15;
    }
}
