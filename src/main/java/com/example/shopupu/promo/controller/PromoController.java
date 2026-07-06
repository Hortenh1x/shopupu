package com.example.shopupu.promo.controller;

import com.example.shopupu.cart.service.CartService;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.promo.dto.ValidatePromoRequest;
import com.example.shopupu.promo.dto.ValidatePromoResponse;
import com.example.shopupu.promo.service.PromoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/promo")
public class PromoController {

    private final PromoService promoService;
    private final CartService cartService;
    private final AccessControlService accessControlService;

    /** Pre-checkout validation against the CURRENT cart subtotal (PROMO-02). */
    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ValidatePromoResponse> validate(@Valid @RequestBody ValidatePromoRequest request) {
        User user = accessControlService.currentUser();
        var cart = cartService.getCart(user.getEmail());
        var promo = promoService.validate(request.code(), user, cart.subtotal());
        var discount = promoService.discountFor(promo, cart.subtotal());
        return ResponseEntity.ok(new ValidatePromoResponse(promo.getCode(), promo.getPromoType(), discount));
    }
}
