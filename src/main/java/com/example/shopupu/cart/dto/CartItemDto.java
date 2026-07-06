package com.example.shopupu.cart.dto;

import java.math.BigDecimal;

public record CartItemDto(
        Long variantId,
        Long productId,
        String title,
        String sku,
        String size,
        String color,
        BigDecimal price,
        Integer quantity,
        BigDecimal lineTotal
) {
}
