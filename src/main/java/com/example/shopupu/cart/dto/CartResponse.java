package com.example.shopupu.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartItemDto> items,
        Integer totalItems,
        BigDecimal subtotal,
        /* non-null only for guest carts: the client must persist it (CART-01) */
        String guestToken
) {
    public CartResponse(List<CartItemDto> items, Integer totalItems, BigDecimal subtotal) {
        this(items, totalItems, subtotal, null);
    }
}
