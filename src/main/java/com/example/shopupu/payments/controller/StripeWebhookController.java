package com.example.shopupu.payments.controller;

import com.example.shopupu.payments.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "stripe")
public class StripeWebhookController {
    private final StripeWebhookService webhook;

    @PostMapping("/api/v1/payments/stripe/webhook")
    public ResponseEntity<Void> handle(@RequestBody String rawPayload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        webhook.handle(rawPayload, signature);
        return ResponseEntity.noContent().build();
    }
}
