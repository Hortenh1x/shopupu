package com.example.shopupu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "shipping")
public class ShippingProperties {

    private String currency = "EUR";
    private Rates rates = new Rates();

    @Data
    public static class Rates {
        private BigDecimal dhl = new BigDecimal("9.99");
        private BigDecimal standardPost = new BigDecimal("4.99");
        private BigDecimal localPickup = BigDecimal.ZERO;
        private BigDecimal defaultRate = new BigDecimal("7.49");
    }
}