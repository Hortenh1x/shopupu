package com.example.shopupu.orders.dto;

import com.example.shopupu.orders.entity.OrderStatus;
import java.time.Instant;

public record OrderStatusHistoryDto(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String changedBy,
        Instant createdAt
) {}
