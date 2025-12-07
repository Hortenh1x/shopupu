package com.example.shopupu.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight DTO for catalog product listings/search.
 */
public record ProductListItem(
        Long id,
        String title,
        BigDecimal price,
        Boolean enabled,
        Instant createdAt,
        Long categoryId,
        String categorySlug
) {
}

