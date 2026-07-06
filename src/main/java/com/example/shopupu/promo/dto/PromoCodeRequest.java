package com.example.shopupu.promo.dto;

import com.example.shopupu.promo.entity.PromoCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record PromoCodeRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code may contain letters, numbers, _ or -")
        String code,

        @NotNull
        PromoCode.Type promoType,

        @DecimalMin("0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal value,

        @DecimalMin("0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal minOrderAmount,

        Instant startsAt,

        Instant endsAt,

        @Min(1)
        Integer maxRedemptions,

        @Min(1)
        Integer perUserLimit,

        Boolean enabled
) {
}
