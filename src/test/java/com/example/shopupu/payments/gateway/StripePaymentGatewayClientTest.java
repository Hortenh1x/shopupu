package com.example.shopupu.payments.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.shopupu.common.exception.ServiceUnavailableException;
import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class StripePaymentGatewayClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private PaymentProperties properties;
    private MockRestServiceServer server;
    private StripePaymentGatewayClient gateway;
    private final PaymentGatewayCreateRequest create = new PaymentGatewayCreateRequest(7L, 12L,
            new BigDecimal("12.34"), "EUR", "operation1");
    private final PaymentGatewayStatusRequest status = new PaymentGatewayStatusRequest(7L, 12L,
            "cs_test_one", new BigDecimal("12.34"), "EUR");

    @BeforeEach
    void setup() {
        properties = new PaymentProperties();
        properties.getStripe().setSecretKey("sk_test_fixture");
        properties.getStripe().setWebhookSecret("whsec_fixture");
        properties.getStripe().setFrontendBaseUrl("https://demo.example.test");
        var builder = RestClient.builder().baseUrl("https://api.stripe.com");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new StripePaymentGatewayClient(properties, mapper, builder.build());
    }

    @Test
    void persistsAndReplaysExactBodyAndProviderKeyAfterConfigurationChanges() throws Exception {
        String body = gateway.snapshotCreateContext(create);
        assertThat(body).contains("unit_amount%5D=1234", "demo.example.test").doesNotContain("sk_test_fixture");
        properties.getStripe().setFrontendBaseUrl("https://changed.example.test");
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().string(body))
                .andExpect(header("Stripe-Version", StripePaymentGatewayClient.API_VERSION))
                .andExpect(header("Idempotency-Key", "shopupu-checkout-operation1"))
                .andExpect(header("Authorization", "Bearer sk_test_fixture"))
                .andRespond(withSuccess(session().toString(), MediaType.APPLICATION_JSON));
        var original = new PaymentGatewayCreateRequest(7L, 12L, create.amount(), "EUR", "operation1", body);
        var response = gateway.recoverPayment(original, Instant.now().minusSeconds(90), Instant.now().plusSeconds(60)).orElseThrow();
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.externalPaymentId()).isEqualTo("cs_test_one");
        assertThat(gateway.recoverPayment(original, Instant.now().minusSeconds(90000), Instant.now().minusSeconds(1))).isEmpty();
        server.verify();
    }

    @Test
    void operatorRecoveryKeepsAnOpenSessionsValidatedCheckoutUrlWithoutCreatingAnotherSession() throws Exception {
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions/cs_test_one"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(session().toString(), MediaType.APPLICATION_JSON));
        var recovered = gateway.recoverKnownSession(status);
        assertThat(recovered.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(recovered.paymentUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_one");
        server.verify();
    }

    @Test
    void unavailableAndLiveKeysFailBeforeNetwork() {
        properties.getStripe().setWebhookSecret("");
        assertThatThrownBy(() -> gateway.createPayment(create)).isInstanceOf(ServiceUnavailableException.class);
        properties.getStripe().setWebhookSecret("whsec_fixture");
        properties.getStripe().setSecretKey("sk_live_fixture");
        assertThatThrownBy(() -> gateway.createPayment(create)).isInstanceOf(ServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void distinguishesConfirmedRejectionFromUnknownServerOutcome() {
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> gateway.createPayment(create)).isInstanceOf(PaymentGatewayCreateException.class);
        assertThatThrownBy(() -> gateway.createPayment(create)).isInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    @Test
    void rejectsMissingOrLiveFlagsWrongBindingAmountCurrencyAndHost() throws Exception {
        for (String field : new String[] { "livemode", "client_reference_id", "amount_total", "currency", "metadata" }) {
            ObjectNode invalid = session();
            invalid.remove(field);
            assertThatThrownBy(() -> StripePaymentGatewayClient.validateSession(invalid, status)).isInstanceOf(IllegalStateException.class);
        }
        ObjectNode live = session().put("livemode", true);
        assertThatThrownBy(() -> StripePaymentGatewayClient.validateSession(live, status)).isInstanceOf(IllegalStateException.class);
        ObjectNode wrongAmount = session().put("amount_total", 1235);
        assertThatThrownBy(() -> StripePaymentGatewayClient.validateSession(wrongAmount, status)).isInstanceOf(IllegalStateException.class);
        assertThat(StripePaymentGatewayClient.isHostedTestUrl("https://checkout.stripe.com/c/pay/cs_test_one")).isTrue();
        for (String url : new String[] { "http://checkout.stripe.com/a", "https://checkout.stripe.com.evil.test/a", "https://user@checkout.stripe.com/a", "javascript:alert(1)" }) {
            assertThat(StripePaymentGatewayClient.isHostedTestUrl(url)).isFalse();
        }
    }

    @Test
    void refundLookupUsesBoundSessionIntentAndOperationWithoutPostingAgain() throws Exception {
        var session = session().put("payment_status", "paid").put("payment_intent", "pi_one");
        var metadata = "\"metadata\":{\"shopupu_order_id\":\"7\",\"shopupu_payment_id\":\"12\",\"shopupu_refund_key\":\"refund1\"}";
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions/cs_test_one"))
                .andRespond(withSuccess(session.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.stripe.com/v1/payment_intents/pi_one"))
                .andRespond(withSuccess("{\"object\":\"payment_intent\",\"id\":\"pi_one\",\"livemode\":false,\"amount\":1234,\"currency\":\"eur\"," + metadata + "}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.stripe.com/v1/refunds?payment_intent=pi_one&limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"object\":\"list\",\"has_more\":false,\"data\":[{\"object\":\"refund\",\"id\":\"re_one\",\"payment_intent\":\"pi_one\",\"amount\":1234,\"currency\":\"eur\",\"status\":\"succeeded\"," + metadata + "}]}", MediaType.APPLICATION_JSON));
        var result = gateway.fetchRefundStatus(new PaymentGatewayRefundRequest(7L, 12L, "cs_test_one", create.amount(), "EUR", "refund1"), null).orElseThrow();
        assertThat(result.status()).isEqualTo(PaymentGatewayRefundStatus.SUCCEEDED);
        server.verify();
    }

    private ObjectNode session() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"object":"checkout.session","id":"cs_test_one","mode":"payment","livemode":false,
                 "client_reference_id":"12","metadata":{"shopupu_order_id":"7","shopupu_payment_id":"12"},
                 "amount_total":1234,"currency":"eur","status":"open","payment_status":"unpaid",
                 "url":"https://checkout.stripe.com/c/pay/cs_test_one"}
                """);
    }
}
