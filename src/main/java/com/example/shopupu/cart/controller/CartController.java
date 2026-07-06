package com.example.shopupu.cart.controller;

import com.example.shopupu.cart.dto.AddOrUpdateItemRequest;
import com.example.shopupu.cart.dto.CartResponse;
import com.example.shopupu.cart.service.CartService;
import com.example.shopupu.cart.service.CartService.CartKey;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


/**
 * Works for both authenticated users and guests (CART-01): anonymous clients
 * pass the X-Cart-Token header they received with their first cart response.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    public static final String CART_TOKEN_HEADER = "X-Cart-Token";

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication auth,
                                                @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken) {
        return withTokenHeader(cartService.getCart(key(auth, cartToken)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(Authentication auth,
                                                @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken,
                                                @Valid @RequestBody AddOrUpdateItemRequest req) {
        return withTokenHeader(cartService.addItem(key(auth, cartToken), req.variantId(), req.quantity()));
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> setQuantity(Authentication auth,
                                                    @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken,
                                                    @PathVariable Long variantId,
                                                    @Valid @RequestBody AddOrUpdateItemRequest req) {
        return withTokenHeader(cartService.setQuantity(key(auth, cartToken), variantId, req.quantity()));
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> removeItem(Authentication auth,
                                                   @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken,
                                                   @PathVariable Long variantId) {
        return withTokenHeader(cartService.removeItem(key(auth, cartToken), variantId));
    }

    @DeleteMapping
    public ResponseEntity<CartResponse> clear(Authentication auth,
                                              @RequestHeader(value = CART_TOKEN_HEADER, required = false) String cartToken) {
        return withTokenHeader(cartService.clear(key(auth, cartToken)));
    }

    private CartKey key(Authentication auth, String cartToken) {
        if (auth != null && auth.isAuthenticated()) {
            return CartKey.user(auth.getName());
        }
        return CartKey.guest(cartToken);
    }

    private ResponseEntity<CartResponse> withTokenHeader(CartResponse response) {
        var builder = ResponseEntity.ok();
        if (response.guestToken() != null) {
            builder.header(CART_TOKEN_HEADER, response.guestToken());
        }
        return builder.body(response);
    }
}
