package com.example.shopupu.orders.dto;

import com.example.shopupu.orders.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status
) {}
