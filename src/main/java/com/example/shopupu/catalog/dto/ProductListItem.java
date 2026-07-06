package com.example.shopupu.catalog.dto;

import com.example.shopupu.catalog.entity.Gender;
import java.math.BigDecimal;
import java.time.Instant;


public record ProductListItem(
        Long id,
        String title,
        String slug,
        BigDecimal price,
        BigDecimal oldPrice,
        String brandName,
        Gender gender,
        Boolean enabled,
        Instant createdAt,
        Long categoryId,
        String categorySlug,
        String imageUrl,
        String imageAltText
) {
}
