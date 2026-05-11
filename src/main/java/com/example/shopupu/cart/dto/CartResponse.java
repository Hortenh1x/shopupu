package com.example.shopupu.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * describes the CartResponse record.
 */
public record CartResponse(
        List<CartItemDto> items,
        Integer totalItems,
        BigDecimal subtotal
) {
}