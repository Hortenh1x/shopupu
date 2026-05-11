package com.example.shopupu.cart.dto;

import java.math.BigDecimal;

/**
 * describes the CartItemDto record.
 */
public record CartItemDto(
        Long productId,
        String title,
        BigDecimal price,
        Integer quantity,
        BigDecimal lineTotal
) {
}