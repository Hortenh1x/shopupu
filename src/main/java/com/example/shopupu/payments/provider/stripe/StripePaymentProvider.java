package com.example.shopupu.payments.provider.stripe;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.payments.provider.PaymentProvider;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class StripePaymentProvider implements PaymentProvider {

    @Value("${app.payments.stripe.apiKey}")
    private String apiKey;

    @Value("${app.payments.successUrl}")
    private String successUrl;

    @Value("${app.payments.cancelUrl}")
    private String cancelUrl;

    // RU: Stripe работает в центах, конвертим BigDecimal → long
    // EN: Stripe expects amounts in the smallest currency unit (cents)
    private static long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    @Override
    public ProviderCreateResponse createCheckout(Order order, BigDecimal amount, String currency, String idempotencyKey) {
        Stripe.apiKey = apiKey;

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(successUrl + "?orderId=" + order.getId())
                        .setCancelUrl(cancelUrl + "?orderId=" + order.getId())
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPriceData(
                                                SessionCreateParams.LineItem.PriceData.builder()
                                                        .setCurrency(currency.toLowerCase())
                                                        .setUnitAmount(toMinorUnits(amount))
                                                        .setProductData(
                                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                        .setName("Order #" + order.getId())
                                                                        .build()
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .putExtraParam("idempotency_key", idempotencyKey) // неофициальный путь
                        .build();

        try {
            // RU: официальная идемпотентность Stripe задаётся на уровне HTTP-заголовка Idempotency-Key.
            // EN: official idempotency uses HTTP header "Idempotency-Key".
            // Stripe Java SDK позволяет передать его через RequestOptions:
            var options = com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey)
                    .build();

            Session session = Session.create(params, options);
            return new ProviderCreateResponse(session.getUrl(), session.getId());
        } catch (StripeException e) {
            // RU: здесь можно подключить ретраи/алёртинг
            // EN: place to add retries/alerting
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    @Override
    public void refund(String providerPaymentId, BigDecimal amount) {
        // опционально: реализовать через Refund.create(...)
    }
}
