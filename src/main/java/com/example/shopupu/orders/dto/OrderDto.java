package com.example.shopupu.orders.dto;

import com.example.shopupu.orders.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;


public record OrderDto(
        Long id,
        String orderNumber,
        BigDecimal subtotalAmount,
        BigDecimal shippingAmount,
        BigDecimal discountAmount,
        String promoCode,
        BigDecimal paymentAmount,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemDto> items
) {}
