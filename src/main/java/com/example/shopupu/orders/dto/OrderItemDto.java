package com.example.shopupu.orders.dto;

import java.math.BigDecimal;

/**
 * RU: DTO позиции заказа (то, что возвращаем на фронт)
 * EN: DTO for an order line item
 */
public record OrderItemDto(
        Long id,
        Long productId,
        String title,
        BigDecimal price,
        Integer quantity,
        BigDecimal lineTotal
) {}
