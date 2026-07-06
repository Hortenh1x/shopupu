package com.example.shopupu.payments.gateway;

import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.payments.entity.PaymentStatus;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Monobank acquiring (UAH payments, PAY-01): creates a hosted invoice; the card
 * data never touches this backend — the customer pays on Monobank's page and the
 * result arrives via the signed webhook.
 * API: POST /api/merchant/invoice/create with X-Token.
 */
@Component
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "monobank")
public class MonobankPaymentGatewayClient implements PaymentGatewayClient {

    private static final int UAH_ISO_4217 = 980;

    private final PaymentProperties paymentProperties;
    private final RestClient restClient;

    public MonobankPaymentGatewayClient(PaymentProperties paymentProperties) {
        this.paymentProperties = paymentProperties;
        Duration timeout = Duration.ofSeconds(paymentProperties.getRequestTimeoutSeconds());
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(required(paymentProperties.getMonobank().getApiBaseUrl(), "payments.monobank.api-base-url"))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public PaymentGatewayCreateResponse createPayment(PaymentGatewayCreateRequest request) {
        if (!"UAH".equalsIgnoreCase(request.currency())) {
            throw new IllegalStateException("Monobank only accepts UAH payments");
        }
        try {
            MonobankInvoiceRequest body = new MonobankInvoiceRequest(
                    toMinorUnits(request.amount()),
                    UAH_ISO_4217,
                    new MerchantPaymInfo("order-" + request.orderId() + "-payment-" + request.paymentId()),
                    paymentProperties.getCallbackUrl()
            );

            MonobankInvoiceResponse response = restClient.post()
                    .uri("/api/merchant/invoice/create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Token", required(paymentProperties.getMonobank().getToken(), "payments.monobank.token"))
                    .body(body)
                    .retrieve()
                    .body(MonobankInvoiceResponse.class);

            if (response == null || response.invoiceId() == null) {
                throw new IllegalStateException("Monobank returned an empty invoice response");
            }

            return new PaymentGatewayCreateResponse(
                    response.invoiceId(),
                    "monobank",
                    PaymentStatus.PENDING,
                    response.pageUrl(),
                    null
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create Monobank invoice", ex);
        }
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured");
        }
        return value;
    }

    private record MonobankInvoiceRequest(
            long amount,
            int ccy,
            MerchantPaymInfo merchantPaymInfo,
            String webHookUrl
    ) {
    }

    private record MerchantPaymInfo(String reference) {
    }

    private record MonobankInvoiceResponse(String invoiceId, String pageUrl) {
    }
}
