package com.example.shopupu.payments.gateway;

import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "bank_back")
public class BankPaymentGatewayClient implements PaymentGatewayClient {

    private static final String CREATE_PAYMENT_PATH = "/api/provider/payments";

    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentGatewayCreateResponse createPayment(PaymentGatewayCreateRequest request) {
        try {
            BankCreatePaymentRequest body = new BankCreatePaymentRequest(
                    request.orderId(),
                    request.paymentId(),
                    request.amount(),
                    request.currency(),
                    paymentProperties.getCallbackUrl(),
                    request.paymentId() + "-" + request.orderId()
            );
            String payload = objectMapper.writeValueAsString(body);
            long timestamp = Instant.now().toEpochMilli();
            String signature = signRequest(timestamp, payload);

            BankCreatePaymentResponse response = RestClient.create(required(paymentProperties.getServiceBaseUrl(), "payments.service-base-url"))
                    .post()
                    .uri(CREATE_PAYMENT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Service-Id", required(paymentProperties.getServiceClientId(), "payments.service-client-id"))
                    .header("X-Timestamp", Long.toString(timestamp))
                    .header("X-Signature", signature)
                    .body(payload)
                    .retrieve()
                    .body(BankCreatePaymentResponse.class);

            if (response == null) {
                throw new IllegalStateException("Bank payment provider returned an empty response");
            }

            return toGatewayResponse(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create payment in Bank_back", ex);
        }
    }

    private String signRequest(long timestamp, String payload) {
        String signedPayload = "POST\n" + CREATE_PAYMENT_PATH + "\n" + timestamp + "\n" + payload;
        String secret = required(paymentProperties.getServiceSecret(), "payments.service-secret");
        return HmacSignature.sign(secret, signedPayload);
    }

    private PaymentGatewayCreateResponse toGatewayResponse(BankCreatePaymentResponse response) {
        return new PaymentGatewayCreateResponse(
                response.externalPaymentId(),
                response.provider(),
                PaymentStatus.valueOf(response.status()),
                response.paymentUrl(),
                response.clientToken()
        );
    }

    private String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured");
        }
        return value;
    }

    private record BankCreatePaymentRequest(
            Long shopOrderId,
            Long shopPaymentId,
            java.math.BigDecimal amount,
            String currency,
            String callbackUrl,
            String idempotencyKey
    ) {
    }

    private record BankCreatePaymentResponse(
            String externalPaymentId,
            String provider,
            String status,
            String paymentUrl,
            String clientToken
    ) {
    }
}
