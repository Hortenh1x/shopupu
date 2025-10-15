package com.example.shopupu.orders.dto;

import com.example.shopupu.orders.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * RU: DTO заказа
 * EN: DTO for order details
 */
public record OrderDto(
        Long id,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemDto> items
) {}
