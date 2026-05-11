package com.example.shopupu.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * describes the AddOrUpdateItemRequest record.
 */
public record AddOrUpdateItemRequest(
        @NotNull
        Long productId,

        @NotNull
        @PositiveOrZero
        Integer quantity
) {
}