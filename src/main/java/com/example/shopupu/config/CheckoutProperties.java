package com.example.shopupu.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.checkout")
public class CheckoutProperties {

    /** How long an unpaid order keeps its inventory reservation before auto-cancel. */
    @Min(5)
    @Max(24 * 60)
    private long pendingPaymentTtlMin = 30;
}
