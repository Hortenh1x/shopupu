package com.example.shopupu.payments.provider;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.payments.entity.Payment;

import java.math.BigDecimal;

public interface PaymentProvider {

    /** RU: Создать платеж/интент у провайдера. EN: Create payment/intent at provider. */
    Payment createIntent(Payment payment);

    /** RU: Подтвердить/захолдить или списать. EN: Confirm (authorize/capture). */
    Payment confirm(String providerPaymentId);

    /** RU: Отмена/void. EN: Cancel/void. */
    Payment cancel(String providerPaymentId);

    // RU: создать платёжную сессию (страница оплаты либо clientSecret)
    // EN: create a checkout session (redirect URL or clientSecret)
    ProviderCreateResponse createCheckout(Order order, BigDecimal amount, String currency, String idempotencyKey);

    // RU: обработка возврата средств (опционально)
    // EN: refund (optional)
    void refund(String providerPaymentId, BigDecimal amount);

    // ===== DTO =====
    record ProviderCreateResponse(String checkoutUrl, String providerPaymentId) {}
}
