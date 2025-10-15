package com.example.shopupu.payments.provider.stripe;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.payments.dto.PaymentEventDto;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.dto.PaymentDto;
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

import java.util.Optional;

/**
 * RU: Реализация платёжного провайдера Stripe.
 * EN: Implementation of Stripe payment provider.
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
     * EN: Creates a payment via Stripe API.
     */
    @Override
    public PaymentResponse createPayment(Order order) {
        try {
            PaymentIntent intent = PaymentIntent.builder()
                    .setAmount(order.getTotalAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue()) // в центах
                    .setCurrency("eur")
                    .putMetadata("orderId", order.getId().toString())
                    .build()
                    .create();

            return new PaymentResponse(
                    intent.getId(),
                    "STRIPE",
                    PaymentStatus.fromStripeStatus(intent.getStatus()),
                    order.getTotalAmount(),
                    intent.getClientSecret()
            );
        } catch (Exception e) {
            log.error("❌ Stripe payment creation failed", e);
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    /**
     * RU: Обработка webhook’ов Stripe с проверкой подписи.
     * EN: Handles Stripe webhooks with signature verification.
     */
    @Override
    public Optional<PaymentEventDto> parseWebhook(String payload, String signature) {
        try {
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);

            if (event.getType().startsWith("payment_intent.")) {
                PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject().orElse(null);

                if (intent != null) {
                    log.info("💳 Stripe webhook: {} for payment {}", event.getType(), intent.getId());
                    return Optional.of(new PaymentEventDto(
                            intent.getId(),
                            PaymentStatus.fromStripeStatus(intent.getStatus())
                    ));
                }
            }

            log.warn("⚠️ Unknown Stripe event type: {}", event.getType());
            return Optional.empty();

        } catch (SignatureVerificationException e) {
            log.error("❌ Invalid Stripe webhook signature", e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("❌ Stripe webhook parsing error", e);
            return Optional.empty();
        }
    }
}
