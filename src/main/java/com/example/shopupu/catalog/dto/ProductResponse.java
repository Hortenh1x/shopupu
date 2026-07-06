package com.example.shopupu.catalog.dto;

import com.example.shopupu.catalog.entity.Gender;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;


public record ProductResponse(
        Long id,
        String title,
        String slug,
        String description,
        BigDecimal price,
        BigDecimal oldPrice,
        Boolean enabled,
        Gender gender,
        String season,
        String material,
        String careInstructions,
        String metaTitle,
        String metaDescription,
        Long brandId,
        String brandName,
        Instant createdAt,
        Long categoryId,
        String categoryName,
        String categorySlug,
        List<ProductResponseImage> images,
        List<VariantResponse> variants
) {
    public record ProductResponseImage(
            Long id,
            String url,
            String altText,
            Integer position
    ) {
    }
}
