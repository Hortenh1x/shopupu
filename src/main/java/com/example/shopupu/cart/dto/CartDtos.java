package com.example.shopupu.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * RU: DTO для API корзины
 * EN: DTOs for cart API
 */
public class CartDtos {

    // RU: Запрос на добавление/обновление позиции
    // EN: Add/Update item request
    public record AddOrUpdateItemRequest(Long productId, Integer quantity) {}

    // RU: Позиция в ответе
    // EN: Line item in response
    public record CartItemDto(
            Long productId,
            String title,
            BigDecimal price,
            Integer quantity,
            BigDecimal lineTotal
    ) {}

    // RU: Корзина в ответе
    // EN: Cart response
    public record CartResponse(
            List<CartItemDto> items,
            Integer totalItems,
            BigDecimal subtotal
    ) {}
}