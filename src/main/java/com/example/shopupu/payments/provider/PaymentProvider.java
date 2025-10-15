package com.example.shopupu.payments.provider;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.dto.PaymentEventDto;

import java.util.Optional;

/**
 * RU: Контракт для интеграции с любыми платёжными провайдерами.
 * EN: Common contract for any payment provider (Stripe, PayPal, etc.)
 */
public interface PaymentProvider {

    /**
     * RU: Создать новый платёж у провайдера.
     * EN: Creates a new payment with the provider.
     */
    PaymentResponse createPayment(Order order);

    /**
     * RU: Обработать webhook от провайдера.
     * EN: Parses and validates incoming webhook.
     */
    Optional<PaymentEventDto> parseWebhook(String payload, String signature);

    /**
     * RU: Получить имя провайдера (например, "STRIPE" или "PAYPAL").
     * EN: Returns provider name.
     */
    String getName();
}
