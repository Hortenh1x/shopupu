package com.example.shopupu.payments.provider.stripe;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.payments.dto.PaymentEventDto;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.provider.PaymentProvider;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RU: Реализация Stripe API (создание платежей + обработка webhook).
 * EN: Stripe integration (create payments + handle webhooks).
 */
@Component("stripe")
@Slf4j
public class StripePaymentProvider implements PaymentProvider {

    private final String apiKey;
    private final String webhookSecret;

    public StripePaymentProvider(
            @Value("${payments.stripe.apiKey}") String apiKey,
            @Value("${payments.stripe.webhookSecret}") String webhookSecret
    ) {
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
        Stripe.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "stripe";
    }

    /**
     * RU: Создание платежа через Stripe API.
     * EN: Creates a new payment through Stripe API.
     */
    @Override
    public PaymentResponse createPayment(Order order) {
        try {
            // Stripe принимает параметры через Map<String, Object>
            Map<String, Object> params = new HashMap<>();
            params.put("amount", order.getTotalAmount().multiply(BigDecimal.valueOf(100)).longValue()); // сумма в центах
            params.put("currency", "eur");

            Map<String, String> metadata = new HashMap<>();
            metadata.put("orderId", order.getId().toString());
            params.put("metadata", metadata);

            // Создание Intent через API Stripe
            PaymentIntent intent = PaymentIntent.create(params);

            log.info("✅ Stripe payment intent created: {}", intent.getId());

            return new PaymentResponse(
                    intent.getId(),
                    "STRIPE",
                    PaymentStatus.fromStripeStatus(intent.getStatus()),
                    order.getTotalAmount(),
                    intent.getClientSecret()
            );

        } catch (Exception e) {
            log.error("❌ Stripe createPayment failed", e);
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    /**
     * RU: Обработка webhook Stripe (с проверкой подписи).
     * EN: Handles Stripe webhook with signature validation.
     */
    @Override
    public Optional<PaymentEventDto> parseWebhook(String payload, String signature) {
        try {
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            log.info("📩 Received Stripe event: {}", event.getType());

            if (event.getType().startsWith("payment_intent.")) {
                PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);

                if (intent == null) return Optional.empty();

                PaymentStatus status = PaymentStatus.fromStripeStatus(intent.getStatus());
                log.info("💳 Stripe webhook payment {} → {}", intent.getId(), status);

                return Optional.of(new PaymentEventDto(intent.getId(), status));
            }

            return Optional.empty();

        } catch (SignatureVerificationException e) {
            log.error("❌ Invalid Stripe signature", e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("❌ Stripe webhook error", e);
            return Optional.empty();
        }
    }

    /**
     * RU: Обёртка для упрощённого вызова из PaymentService.
     * EN: Helper for direct webhook handling (used by PaymentService).
     */
    public void handleWebhook(String payload, String signature) {
        parseWebhook(payload, signature)
                .ifPresentOrElse(
                        data -> log.info("Webhook handled successfully for {}", data.externalPaymentId()),
                        () -> log.warn("Webhook parsing failed")
                );
    }
}
