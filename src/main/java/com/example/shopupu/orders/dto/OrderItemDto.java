package com.example.shopupu.orders.dto;

import java.math.BigDecimal;


/**
 * describes the OrderItemDto record.
 */
public record OrderItemDto(
        Long id,
        Long productId,
        String title,
        BigDecimal price,
        Integer quantity,
        BigDecimal lineTotal
) {}