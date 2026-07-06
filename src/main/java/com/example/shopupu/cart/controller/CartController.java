package com.example.shopupu.cart.controller;

import com.example.shopupu.cart.dto.AddOrUpdateItemRequest;
import com.example.shopupu.cart.dto.CartResponse;
import com.example.shopupu.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> getCart(Authentication auth) {
        return ResponseEntity.ok(cartService.getCart(auth.getName()));
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> addItem(Authentication auth,
                                                @Valid @RequestBody AddOrUpdateItemRequest req) {
        return ResponseEntity.ok(cartService.addItem(auth.getName(), req.variantId(), req.quantity()));
    }

    @PutMapping("/items/{variantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> setQuantity(Authentication auth,
                                                    @PathVariable Long variantId,
                                                    @Valid @RequestBody AddOrUpdateItemRequest req) {
        return ResponseEntity.ok(cartService.setQuantity(auth.getName(), variantId, req.quantity()));
    }

    @DeleteMapping("/items/{variantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> removeItem(Authentication auth,
                                                   @PathVariable Long variantId) {
        return ResponseEntity.ok(cartService.removeItem(auth.getName(), variantId));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CartResponse> clear(Authentication auth) {
        return ResponseEntity.ok(cartService.clear(auth.getName()));
    }
}
