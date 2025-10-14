package com.example.shopupu.cart.controller;

import com.example.shopupu.cart.dto.CartDtos.*;
import com.example.shopupu.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * RU: REST API корзины (требует аутентификации)
 * EN: Cart REST API (requires authentication)
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // RU: Получить корзину
    // EN: Get cart
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> getCart(Authentication auth) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.getCart(email));
    }

    // RU: Добавить товар (или увеличить количество, если уже есть)
    // EN: Add item (or increase quantity if exists)
    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> addItem(Authentication auth,
                                                @Valid @RequestBody AddOrUpdateItemRequest req) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.addItem(email, req.productId(), req.quantity()));
    }

    // RU: Установить точное количество (0 — удалить)
    // EN: Set exact quantity (0 — remove)
    @PutMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> setQuantity(Authentication auth,
                                                    @PathVariable Long productId,
                                                    @Valid @RequestBody AddOrUpdateItemRequest req) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.setQuantity(email, productId, req.quantity()));
    }

    // RU: Удалить товар из корзины
    // EN: Remove item from cart
    @DeleteMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> removeItem(Authentication auth,
                                                   @PathVariable Long productId) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.removeItem(email, productId));
    }

    // RU: Очистить корзину
    // EN: Clear cart
    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> clear(Authentication auth) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.clear(email));
    }

}