package com.example.shopupu.payments.gateway;

import com.example.shopupu.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "bank_back")
/**
 * verifies callbacks sent by the Bank_back payment provider.
 */
public class BankPaymentCallbackVerifier implements PaymentCallbackVerifier {

    private final PaymentProperties paymentProperties;

    @Override
    // handles isValid.
    public boolean isValid(String payload, String signature) {
        String secret = paymentProperties.getCallbackSecret();
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        return HmacSignature.matches(HmacSignature.sign(secret, payload), signature);
    }
}
