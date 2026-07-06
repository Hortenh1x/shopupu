package com.example.shopupu.catalog.dto;

import java.math.BigDecimal;

public record VariantResponse(
        Long id,
        String sku,
        String size,
        String color,
        BigDecimal price,
        BigDecimal oldPrice,
        Boolean enabled,
        Integer available
) {
}
