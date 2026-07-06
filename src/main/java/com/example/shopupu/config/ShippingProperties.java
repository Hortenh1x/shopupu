package com.example.shopupu.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "shipping")
public class ShippingProperties {

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "shipping.currency must be a 3-letter ISO code")
    private String currency = "EUR";

    @NotNull
    @PositiveOrZero
    private BigDecimal freeShippingThreshold = new BigDecimal("100.00");

    @NotNull
    private Rates rates = new Rates();

    @Data
    public static class Rates {
        @NotNull
        @PositiveOrZero
        private BigDecimal dhl = new BigDecimal("9.99");

        @NotNull
        @PositiveOrZero
        private BigDecimal standardPost = new BigDecimal("4.99");

        @NotNull
        @PositiveOrZero
        private BigDecimal localPickup = BigDecimal.ZERO;

        @NotNull
        @PositiveOrZero
        private BigDecimal defaultRate = new BigDecimal("7.49");
    }
}
