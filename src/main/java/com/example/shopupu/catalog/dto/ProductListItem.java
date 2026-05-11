package com.example.shopupu.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;


/**
 * describes the ProductListItem record.
 */
public record ProductListItem(
        Long id,
        String title,
        BigDecimal price,
        Boolean enabled,
        Instant createdAt,
        Long categoryId,
        String categorySlug,
        String imageUrl,
        String imageAltText
) {
}
