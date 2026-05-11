package com.example.shopupu.reviews.dto;

import java.math.BigDecimal;

/**
 * describes the ProductRatingSummaryResponse record.
 */
public record ProductRatingSummaryResponse(
        Long productId,
        BigDecimal averageRating,
        Long reviewCount
) {
}
