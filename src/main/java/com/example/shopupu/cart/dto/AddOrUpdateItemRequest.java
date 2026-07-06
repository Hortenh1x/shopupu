package com.example.shopupu.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AddOrUpdateItemRequest(
        @NotNull
        Long variantId,

        @NotNull
        @PositiveOrZero
        Integer quantity
) {
}
