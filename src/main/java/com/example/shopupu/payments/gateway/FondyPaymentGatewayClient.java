package com.example.shopupu.payments.gateway;

import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.payments.entity.PaymentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fondy hosted checkout (EUR / foreign cards, PAY-01): creates a checkout URL,
 * the customer enters the card on Fondy's page (PCI scope stays with Fondy).
 * Requests are signed with SHA1 over '|'-joined sorted params per Fondy docs.
 */
@Component
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "fondy")
public class FondyPaymentGatewayClient implements PaymentGatewayClient {

    private final PaymentProperties paymentProperties;
    private final RestClient restClient;

    public FondyPaymentGatewayClient(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
        Duration timeout = Duration.ofSeconds(paymentProperties.getRequestTimeoutSeconds());
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(required(paymentProperties.getFondy().getApiBaseUrl(), "payments.fondy.api-base-url"))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PaymentGatewayCreateResponse createPayment(PaymentGatewayCreateRequest request) {
        try {
            String orderId = "shopupu-" + request.orderId() + "-" + request.paymentId();
            TreeMap<String, String> params = new TreeMap<>(Map.of(
                    "merchant_id", required(paymentProperties.getFondy().getMerchantId(), "payments.fondy.merchant-id"),
                    "order_id", orderId,
                    "order_desc", "Order " + request.orderId(),
                    "amount", String.valueOf(toMinorUnits(request.amount())),
                    "currency", request.currency(),
                    "server_callback_url", paymentProperties.getCallbackUrl()
            ));
            params.put("signature", sign(params));

            FondyResponse response = restClient.post()
                    .uri("/checkout/url/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("request", params))
                    .retrieve()
                    .body(FondyResponse.class);

            if (response == null || response.response() == null
                    || response.response().checkout_url() == null) {
                throw new IllegalStateException("Fondy returned an empty checkout response");
            }

            return new PaymentGatewayCreateResponse(
                    orderId,
                    "fondy",
                    PaymentStatus.PENDING,
                    response.response().checkout_url(),
                    response.response().payment_id()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create Fondy checkout", ex);
        }
    }

    /** SHA1(password|param1|param2|...) over non-empty params sorted by key. */
    private String sign(TreeMap<String, String> params) {
        String secret = required(paymentProperties.getFondy().getSecret(), "payments.fondy.secret");
        StringBuilder joined = new StringBuilder(secret);
        for (String value : params.values()) {
            if (value != null && !value.isBlank()) {
                joined.append('|').append(value);
            }
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(joined.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign Fondy request", ex);
        }
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured");
        }
        return value;
    }

    private record FondyResponse(FondyResponseBody response) {
    }

    private record FondyResponseBody(String checkout_url, String payment_id, String response_status) {
    }
}
