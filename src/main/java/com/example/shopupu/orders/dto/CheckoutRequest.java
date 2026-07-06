package com.example.shopupu.orders.dto;

import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @Size(max = 64) String promoCode
) {
}
