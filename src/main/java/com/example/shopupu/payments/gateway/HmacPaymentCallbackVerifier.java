package com.example.shopupu.payments.gateway;

import com.example.shopupu.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Fail-closed HMAC-SHA256 verification of the relayed provider webhooks for
 * monobank/fondy deployments (SEC-18/PAY-03). The edge that receives the
 * provider's native signature re-signs the payload with the shared secret.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "'${payments.default-provider}' == 'monobank' or '${payments.default-provider}' == 'fondy'")
public class HmacPaymentCallbackVerifier implements PaymentCallbackVerifier {

    private final PaymentProperties paymentProperties;

    @Override
    public boolean isValid(String payload, String signature) {
        String secret = paymentProperties.getCallbackSecret();
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        return HmacSignature.matches(HmacSignature.sign(secret, payload), signature);
    }
}
