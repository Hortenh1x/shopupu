package com.example.shopupu.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Detailed representation of a catalog product for clients.
 */
public record ProductResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String sku,
        Integer stock,
        Boolean enabled,
        Instant createdAt,
        Long categoryId,
        String categoryName,
        String categorySlug,
        List<ProductResponseImage> images
) {
    public record ProductResponseImage(
            Long id,
            String url,
            String altText,
            Integer position
    ) {
    }
}

