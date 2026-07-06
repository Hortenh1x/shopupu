package com.example.shopupu.identity.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WishlistEntryResponse(
        Long productId,
        String title,
        String slug,
        BigDecimal price,
        BigDecimal oldPrice,
        String brandName,
        Boolean available,
        Instant addedAt
) {
}
