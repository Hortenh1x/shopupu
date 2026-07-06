package com.example.shopupu.orders.dto;

import java.math.BigDecimal;


public record OrderItemDto(
        Long id,
        Long productId,
        Long variantId,
        String title,
        String sku,
        String size,
        String color,
        String brand,
        BigDecimal price,
        Integer quantity,
        BigDecimal lineTotal
) {}
