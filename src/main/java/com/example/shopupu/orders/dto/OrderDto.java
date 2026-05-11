package com.example.shopupu.orders.dto;

import com.example.shopupu.orders.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;


/**
 * describes the OrderDto record.
 */
public record OrderDto(
        Long id,
        BigDecimal subtotalAmount,
        BigDecimal shippingAmount,
        BigDecimal paymentAmount,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemDto> items
) {}