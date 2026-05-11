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
@RequestMapping("/api/cart")
@RequiredArgsConstructor
/**
 * describes the CartController class.
 */
public class CartController {

    private final CartService cartService;



    @GetMapping
    @PreAuthorize("isAuthenticated()")
    // handles getCart.
    public ResponseEntity<CartResponse> getCart(Authentication auth) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.getCart(email));
    }



    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    // handles addItem.
    public ResponseEntity<CartResponse> addItem(Authentication auth,
                                                @Valid @RequestBody AddOrUpdateItemRequest req) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.addItem(email, req.productId(), req.quantity()));
    }



    @PutMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    // handles setQuantity.
    public ResponseEntity<CartResponse> setQuantity(Authentication auth,
                                                    @PathVariable Long productId,
                                                    @Valid @RequestBody AddOrUpdateItemRequest req) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.setQuantity(email, productId, req.quantity()));
    }



    @DeleteMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    // handles removeItem.
    public ResponseEntity<CartResponse> removeItem(Authentication auth,
                                                   @PathVariable Long productId) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.removeItem(email, productId));
    }



    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    // handles clear.
    public ResponseEntity<CartResponse> clear(Authentication auth) {
        String email = auth.getName();
        return ResponseEntity.ok(cartService.clear(email));
    }

}