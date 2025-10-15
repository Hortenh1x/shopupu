package com.example.shopupu.payments.controller;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.provider.PaymentProvider;
import com.example.shopupu.payments.provider.stripe.StripePaymentProvider;
import com.example.shopupu.payments.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * RU: Контроллер для платежей (создание и webhook-и).
 * EN: Payment controller (create payments and handle webhooks).
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final StripePaymentProvider stripePaymentProvider;

    /**
     * RU: Создание нового платежа для заказа.
     * EN: Create a new payment for the given order.
     */
    @PostMapping("/create/{orderId}")
    public ResponseEntity<PaymentResponse> createPayment(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        PaymentResponse response = paymentService.processPayment(order);
        return ResponseEntity.ok(response);
    }

    /**
     * RU: Webhook от Stripe (обработка уведомлений).
     * EN: Webhook endpoint for Stripe notifications.
     */
    @PostMapping("/webhook/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String signatureHeader,
            @RequestBody String payload
    ) {
        try {
            stripePaymentProvider.handleWebhook(payload, signatureHeader);
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Ошибка обработки Stripe webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
        }
    }

    /**
     * RU: Для отладки — тестовый webhook вызов без подписи.
     * EN: Test-only webhook endpoint.
     */
    @PostMapping("/webhook/test")
    public ResponseEntity<Map<String, String>> testWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Webhook received (test): {}", payload);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
