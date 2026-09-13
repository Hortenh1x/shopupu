package com.example.shopupu.payments.gateway;

import com.example.shopupu.common.exception.ServiceUnavailableException;
import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Hosted test checkout only. Provider objects are bound to the immutable local payment snapshot. */
@Component
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "stripe")
public class StripePaymentGatewayClient implements PaymentGatewayClient {
    public static final String API_VERSION = "2025-06-30.basil";
    private final PaymentProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client;

    @Autowired
    public StripePaymentGatewayClient(PaymentProperties properties, ObjectMapper mapper) {
        this(properties, mapper, buildClient(properties));
    }

    StripePaymentGatewayClient(PaymentProperties properties, ObjectMapper mapper, RestClient client) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = client;
        frontendUrl(properties.getStripe().getFrontendBaseUrl());
    }

    private static RestClient buildClient(PaymentProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.getRequestTimeoutSeconds());
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl("https://api.stripe.com").requestFactory(factory).build();
    }

    @Override
    public void ensureAvailable() {
        if (!properties.getStripe().isAvailable()
                || !properties.getStripe().getSecretKey().matches("sk_test_[A-Za-z0-9]+")) {
            throw new ServiceUnavailableException("STRIPE_TEST_UNAVAILABLE", "Stripe test checkout has not been connected yet");
        }
    }

    @Override
    public String snapshotCreateContext(PaymentGatewayCreateRequest request) {
        ensureAvailable();
        require(request.idempotencyKey() != null && !request.idempotencyKey().isBlank(), "Missing payment operation key");
        long amount = minorUnits(request.amount(), request.currency());
        var form = new LinkedMultiValueMap<String, String>();
        form.add("mode", "payment");
        form.add("locale", "auto");
        form.add("payment_method_types[0]", "card");
        form.add("client_reference_id", request.paymentId().toString());
        String returnUrl = frontendUrl(properties.getStripe().getFrontendBaseUrl()) + "/payment/" + request.paymentId();
        form.add("success_url", returnUrl);
        form.add("cancel_url", returnUrl + "?canceled=1");
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", request.currency().toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]", Long.toString(amount));
        form.add("line_items[0][price_data][product_data][name]", "shopupu demo order " + request.orderId());
        form.add("custom_text[submit][message]", "Fictional products. Test payment only. No delivery or real charge.");
        addMetadata(form, "metadata", request.orderId(), request.paymentId());
        addMetadata(form, "payment_intent_data[metadata]", request.orderId(), request.paymentId());
        return form.entrySet().stream().flatMap(entry -> entry.getValue().stream()
                .map(value -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8)))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    @Override
    public PaymentGatewayCreateResponse createPayment(PaymentGatewayCreateRequest request) {
        ensureAvailable();
        String body = request.providerRequestContext() != null ? request.providerRequestContext() : snapshotCreateContext(request);
        JsonNode session;
        try {
            session = json(client.post().uri("/v1/checkout/sessions")
                    .header("Authorization", "Bearer " + properties.getStripe().getSecretKey())
                    .header("Stripe-Version", API_VERSION)
                    .header("Idempotency-Key", "shopupu-checkout-" + request.idempotencyKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(String.class));
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 409 && status != 429) {
                throw new PaymentGatewayCreateException("Stripe rejected the test checkout request", ex);
            }
            throw ex;
        }
        validateSession(session, new PaymentGatewayStatusRequest(request.orderId(), request.paymentId(),
                session.path("id").asText(), request.amount(), request.currency()));
        String url = session.path("url").asText();
        require(isHostedTestUrl(url), "Unexpected Stripe checkout URL");
        return new PaymentGatewayCreateResponse(session.path("id").asText(), "stripe", PaymentStatus.PENDING, url, null);
    }

    @Override
    public Optional<PaymentGatewayCreateResponse> recoverPayment(PaymentGatewayCreateRequest original,
            Instant preparedAt, Instant retryUntil) {
        Instant now = Instant.now();
        if (preparedAt == null || retryUntil == null || preparedAt.isAfter(now) || !now.isBefore(retryUntil)
                || original.providerRequestContext() == null) return Optional.empty();
        return Optional.of(createPayment(original));
    }

    @Override
    public Optional<PaymentStatus> fetchPaymentStatus(PaymentGatewayStatusRequest request) {
        ensureAvailable();
        JsonNode session = fetchSession(request);
        if ("paid".equals(session.path("payment_status").asText())) return Optional.of(PaymentStatus.SUCCEEDED);
        if ("expired".equals(session.path("status").asText()) && "unpaid".equals(session.path("payment_status").asText())) {
            return Optional.of(PaymentStatus.EXPIRED);
        }
        return Optional.of(PaymentStatus.PENDING);
    }

    /** Read-only operator recovery, including a usable URL when the original checkout is still open. */
    public PaymentGatewayCreateResponse recoverKnownSession(PaymentGatewayStatusRequest request) {
        ensureAvailable();
        JsonNode session = fetchSession(request);
        PaymentStatus state = "paid".equals(session.path("payment_status").asText()) ? PaymentStatus.SUCCEEDED
                : "expired".equals(session.path("status").asText()) && "unpaid".equals(session.path("payment_status").asText())
                ? PaymentStatus.EXPIRED : PaymentStatus.PENDING;
        String url = null;
        if (state == PaymentStatus.PENDING) {
            url = session.path("url").asText();
            require(isHostedTestUrl(url), "The open test session has no usable hosted checkout URL");
        }
        return new PaymentGatewayCreateResponse(request.externalPaymentId(), "stripe", state, url, null);
    }

    @Override
    public PaymentGatewayRefundResponse refundPayment(PaymentGatewayRefundRequest request) {
        ensureAvailable();
        String intent = validatedPaymentIntent(request);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("payment_intent", intent);
        form.add("amount", Long.toString(minorUnits(request.amount(), request.currency())));
        addMetadata(form, "metadata", request.orderId(), request.paymentId());
        form.add("metadata[shopupu_refund_key]", request.idempotencyKey());
        return validateRefund(post("/v1/refunds", form, "shopupu-refund-" + request.idempotencyKey()), request, intent);
    }

    @Override
    public Optional<PaymentGatewayRefundResponse> fetchRefundStatus(PaymentGatewayRefundRequest request, String externalRefundId) {
        ensureAvailable();
        String intent = validatedPaymentIntent(request);
        if (externalRefundId != null && !externalRefundId.isBlank()) {
            require(externalRefundId.matches("re_[A-Za-z0-9]+"), "Invalid Stripe refund ID");
            return Optional.of(validateRefund(get("/v1/refunds/" + externalRefundId), request, intent));
        }
        // Recover a lost create response by its durable operation metadata. Never resubmit an unknown refund.
        String after = null;
        for (int page = 0; page < 10; page++) {
            String path = "/v1/refunds?payment_intent=" + intent + "&limit=100" + (after == null ? "" : "&starting_after=" + after);
            JsonNode list = get(path);
            require("list".equals(list.path("object").asText()) && list.path("data").isArray(), "Invalid Stripe refund list");
            for (JsonNode refund : list.path("data")) {
                if (request.idempotencyKey().equals(refund.path("metadata").path("shopupu_refund_key").asText())) {
                    return Optional.of(validateRefund(refund, request, intent));
                }
                after = refund.path("id").asText();
                require(after.matches("re_[A-Za-z0-9]+"), "Invalid Stripe refund ID");
            }
            if (!list.path("has_more").asBoolean() || list.path("data").isEmpty()) break;
        }
        return Optional.empty();
    }

    private String validatedPaymentIntent(PaymentGatewayRefundRequest request) {
        JsonNode session = fetchSession(new PaymentGatewayStatusRequest(request.orderId(), request.paymentId(),
                request.externalPaymentId(), request.amount(), request.currency()));
        require("paid".equals(session.path("payment_status").asText()), "Only a paid test session can be refunded");
        String intentId = session.path("payment_intent").asText();
        require(intentId.matches("pi_[A-Za-z0-9]+"), "Missing Stripe payment intent");
        JsonNode intent = get("/v1/payment_intents/" + intentId);
        require("payment_intent".equals(intent.path("object").asText()) && intentId.equals(intent.path("id").asText()), "Wrong payment intent");
        requireTestObject(intent);
        validateAmount(intent, "amount", request.amount(), request.currency());
        validateMetadata(intent, request.orderId(), request.paymentId());
        return intentId;
    }

    /** Called only after the native event signature and explicit test-mode flag have been verified. */
    public PaymentGatewayRefundResponse verifyRefundEvent(JsonNode refund, PaymentGatewayRefundRequest request) {
        ensureAvailable();
        return validateRefund(refund, request, validatedPaymentIntent(request));
    }

    private JsonNode fetchSession(PaymentGatewayStatusRequest request) {
        require(request.externalPaymentId() != null && request.externalPaymentId().matches("cs_test_[A-Za-z0-9]+"), "Invalid test session ID");
        JsonNode session = get("/v1/checkout/sessions/" + request.externalPaymentId());
        validateSession(session, request);
        return session;
    }

    public static void validateSession(JsonNode session, PaymentGatewayStatusRequest request) {
        require("checkout.session".equals(session.path("object").asText()), "Expected a Checkout Session");
        requireTestObject(session);
        require("payment".equals(session.path("mode").asText()), "Expected payment mode");
        require(request.externalPaymentId().equals(session.path("id").asText())
                && session.path("id").asText().matches("cs_test_[A-Za-z0-9]+"), "Wrong test session");
        require(request.paymentId().toString().equals(session.path("client_reference_id").asText()), "Wrong payment reference");
        validateMetadata(session, request.orderId(), request.paymentId());
        validateAmount(session, "amount_total", request.amount(), request.currency());
    }

    public static PaymentGatewayRefundResponse validateRefund(JsonNode refund, PaymentGatewayRefundRequest request, String intent) {
        require("refund".equals(refund.path("object").asText()), "Expected a refund");
        require(refund.path("id").asText().matches("re_[A-Za-z0-9]+"), "Invalid refund ID");
        require(intent.equals(refund.path("payment_intent").asText()), "Wrong refund payment intent");
        validateAmount(refund, "amount", request.amount(), request.currency());
        validateMetadata(refund, request.orderId(), request.paymentId());
        require(request.idempotencyKey().equals(refund.path("metadata").path("shopupu_refund_key").asText()), "Wrong refund operation");
        PaymentGatewayRefundStatus status = switch (refund.path("status").asText()) {
            case "succeeded" -> PaymentGatewayRefundStatus.SUCCEEDED;
            case "pending", "requires_action" -> PaymentGatewayRefundStatus.PENDING;
            case "failed" -> PaymentGatewayRefundStatus.FAILED;
            case "canceled" -> PaymentGatewayRefundStatus.CANCELED;
            default -> PaymentGatewayRefundStatus.UNKNOWN;
        };
        return new PaymentGatewayRefundResponse(refund.path("id").asText(), status);
    }

    public static void requireTestObject(JsonNode node) {
        require(node.path("livemode").isBoolean() && !node.path("livemode").booleanValue(), "Only explicitly marked Stripe test objects are allowed");
    }

    private static void validateMetadata(JsonNode node, Long orderId, Long paymentId) {
        require(orderId.toString().equals(node.path("metadata").path("shopupu_order_id").asText())
                && paymentId.toString().equals(node.path("metadata").path("shopupu_payment_id").asText()), "Stripe metadata does not match this payment");
    }

    private static void validateAmount(JsonNode node, String field, BigDecimal amount, String currency) {
        require(node.path(field).isIntegralNumber() && node.path(field).canConvertToLong()
                && node.path(field).longValue() == minorUnits(amount, currency)
                && currency.equalsIgnoreCase(node.path("currency").asText()), "Stripe amount or currency does not match this payment");
    }

    public static long minorUnits(BigDecimal amount, String currency) {
        require("EUR".equalsIgnoreCase(currency), "The demo checkout supports EUR only");
        long minor = amount.movePointRight(2).longValueExact();
        require(minor > 0, "Payment amount must be positive");
        return minor;
    }

    private static void addMetadata(LinkedMultiValueMap<String, String> form, String prefix, Long orderId, Long paymentId) {
        form.add(prefix + "[shopupu_order_id]", orderId.toString());
        form.add(prefix + "[shopupu_payment_id]", paymentId.toString());
    }

    private JsonNode get(String path) {
        return json(client.get().uri(path).header("Authorization", "Bearer " + properties.getStripe().getSecretKey())
                .header("Stripe-Version", API_VERSION).retrieve().body(String.class));
    }

    private JsonNode post(String path, LinkedMultiValueMap<String, String> form, String key) {
        return json(client.post().uri(path).header("Authorization", "Bearer " + properties.getStripe().getSecretKey())
                .header("Stripe-Version", API_VERSION).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class));
    }

    private JsonNode json(String body) {
        try {
            require(body != null && !body.isBlank(), "Empty Stripe response");
            return mapper.readTree(body);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Invalid Stripe response", ex);
        }
    }

    private static String frontendUrl(String value) {
        URI uri = URI.create(value);
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        require(uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && ("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme())), "Configure a trusted HTTPS frontend URL (HTTP is allowed only on localhost)");
        return value.replaceAll("/+$", "");
    }

    public static boolean isHostedTestUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && "checkout.stripe.com".equals(uri.getHost()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
