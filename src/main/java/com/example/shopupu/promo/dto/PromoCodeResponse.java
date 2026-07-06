package com.example.shopupu.promo.dto;

import com.example.shopupu.promo.entity.PromoCode;
import java.math.BigDecimal;
import java.time.Instant;

public record PromoCodeResponse(
        Long id,
        String code,
        PromoCode.Type promoType,
        BigDecimal value,
        BigDecimal minOrderAmount,
        Instant startsAt,
        Instant endsAt,
        Integer maxRedemptions,
        int perUserLimit,
        int redemptionCount,
        Boolean enabled
) {
    public static PromoCodeResponse from(PromoCode promo) {
        return new PromoCodeResponse(
                promo.getId(),
                promo.getCode(),
                promo.getPromoType(),
                promo.getValue(),
                promo.getMinOrderAmount(),
                promo.getStartsAt(),
                promo.getEndsAt(),
                promo.getMaxRedemptions(),
                promo.getPerUserLimit(),
                promo.getRedemptionCount(),
                promo.getEnabled()
        );
    }
}
