package com.example.shopupu.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Bucket size for auth endpoints (login/register/refresh), per client IP. */
    @Min(1)
    private long authCapacity = 10;

    @Min(1)
    private long authRefillPerMinute = 10;

    /** Bucket size for checkout/payment endpoints, per client IP. */
    @Min(1)
    private long checkoutCapacity = 30;

    @Min(1)
    private long checkoutRefillPerMinute = 30;

    /** Bucket size for semantic/NL search (each query may hit an external AI API), per client IP. */
    @Min(1)
    private long semanticCapacity = 30;

    @Min(1)
    private long semanticRefillPerMinute = 30;
}
