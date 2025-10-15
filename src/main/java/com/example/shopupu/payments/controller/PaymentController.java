package com.example.shopupu.payments.controller;

import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RU: Контроллер для работы с платежами.
 * EN: REST controller for payment operations.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * RU: Создание нового платежа для заказа.
     * EN: Creates a new payment for an order.
     */
    @PostMapping("/create")
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestParam Long orderId,
            @RequestParam(defaultValue = "stripe") String provider
    ) {
        PaymentResponse payment = paymentService.createPayment(orderId, provider);
        return ResponseEntity.ok(payment);
    }

    /**
     * RU: Получение платежа по ID.
     * EN: Gets payment by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    /**
     * RU: Webhook от провайдера (Stripe, PayPal).
     * EN: Webhook endpoint for provider callbacks.
     *
     * ⚠️ Важно: этот эндпоинт должен быть открыт в SecurityConfig!
     */
    @PostMapping("/webhook/{provider}")
    public ResponseEntity<String> handleWebhook(
            @PathVariable String provider,
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature
    ) {
        paymentService.handleWebhook(provider, payload, signature);
        return ResponseEntity.ok("Webhook received");
    }
}
