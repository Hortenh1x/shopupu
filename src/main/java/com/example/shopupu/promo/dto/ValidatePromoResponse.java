package com.example.shopupu.promo.dto;

import com.example.shopupu.promo.entity.PromoCode;
import java.math.BigDecimal;

public record ValidatePromoResponse(
        String code,
        PromoCode.Type promoType,
        BigDecimal discount
) {
}
