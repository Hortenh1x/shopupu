package com.example.shopupu.payments.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RU: Фабрика для выбора нужного платёжного провайдера.
 * EN: Factory to get the right payment provider by name.
 */
@Component
@RequiredArgsConstructor
public class PaymentProviderFactory {

    private final Map<String, PaymentProvider> providers;

    /**
     * RU: Возвращает нужный провайдер по имени (например, "stripe" или "paypal").
     * EN: Returns provider by name (e.g. "stripe", "paypal").
     */
    public PaymentProvider getProvider(String name) {
        if (name == null) return null;
        return providers.get(name.toLowerCase());
    }
}
