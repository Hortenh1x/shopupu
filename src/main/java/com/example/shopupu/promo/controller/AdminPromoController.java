package com.example.shopupu.promo.controller;

import com.example.shopupu.promo.dto.PromoCodeRequest;
import com.example.shopupu.promo.dto.PromoCodeResponse;
import com.example.shopupu.promo.service.PromoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/promo")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminPromoController {

    private final PromoService promoService;

    @GetMapping
    public ResponseEntity<Page<PromoCodeResponse>> list(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(promoService.list(pageable).map(PromoCodeResponse::from));
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponse> create(@Valid @RequestBody PromoCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PromoCodeResponse.from(promoService.create(request)));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<PromoCodeResponse> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return ResponseEntity.ok(PromoCodeResponse.from(promoService.setEnabled(id, enabled)));
    }
}
