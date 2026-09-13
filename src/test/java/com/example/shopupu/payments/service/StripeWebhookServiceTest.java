package com.example.shopupu.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.payments.dto.PaymentCallbackRequest;
import com.example.shopupu.payments.dto.PaymentProviderSnapshot;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.gateway.PaymentGatewayRefundResponse;
import com.example.shopupu.payments.gateway.PaymentGatewayRefundStatus;
import com.example.shopupu.payments.gateway.StripePaymentGatewayClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StripeWebhookServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private PaymentService payments;
    private StripePaymentGatewayClient stripe;
    private StripeWebhookService service;

    @BeforeEach
    void setup() {
        var properties = new PaymentProperties();
        properties.getStripe().setWebhookSecret("whsec_fixture");
        payments = mock(PaymentService.class);
        stripe = mock(StripePaymentGatewayClient.class);
        service = new StripeWebhookService(properties, mapper, payments, stripe);
    }

    @Test
    void verifiesRawSignatureBeforeReadingAnyPayment() throws Exception {
        String raw = event().toString();
        assertThatThrownBy(() -> service.handle(raw + " ", signature(raw))).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(payments, stripe);
    }

    @Test
    void onlyMatchingPaidTestSessionCanApplySuccessIncludingFastCallback() throws Exception {
        when(payments.getProviderSnapshot(12L)).thenReturn(snapshot(null, null));
        String raw = event().toString();
        service.handle(raw, signature(raw));
        var callback = ArgumentCaptor.forClass(PaymentCallbackRequest.class);
        verify(payments).applyVerifiedCallback(eq("stripe"), callback.capture());
        assertThat(callback.getValue().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(callback.getValue().externalPaymentId()).isEqualTo("cs_test_one");
        assertThat(callback.getValue().localPaymentId()).isEqualTo(12L);
    }

    @Test
    void rejectsLiveEventsMissingObjectFlagAndWrongAmountsWithoutStateChanges() throws Exception {
        ObjectNode live = event().put("livemode", true);
        assertThatThrownBy(() -> service.handle(live.toString(), signature(live.toString()))).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payments);
        when(payments.getProviderSnapshot(12L)).thenReturn(snapshot("cs_test_one", null));
        ObjectNode wrong = event();
        ((ObjectNode) wrong.path("data").path("object")).put("amount_total", 1235);
        assertThatThrownBy(() -> service.handle(wrong.toString(), signature(wrong.toString()))).isInstanceOf(IllegalStateException.class);
        ObjectNode missing = event();
        ((ObjectNode) missing.path("data").path("object")).remove("livemode");
        assertThatThrownBy(() -> service.handle(missing.toString(), signature(missing.toString()))).isInstanceOf(IllegalStateException.class);
        verify(payments, never()).applyVerifiedCallback(any(), any());
    }

    @Test
    void lateRefundUsesItsSignedOperationKeyRatherThanTheCurrentAttempt() throws Exception {
        when(payments.getProviderSnapshot(12L)).thenReturn(snapshot("cs_test_one", "new-operation"));
        var result = new PaymentGatewayRefundResponse("re_old", PaymentGatewayRefundStatus.FAILED);
        when(stripe.verifyRefundEvent(any(), any())).thenReturn(result);
        ObjectNode event = event().put("type", "refund.failed");
        ObjectNode refund = mapper.createObjectNode().put("object", "refund").put("id", "re_old");
        refund.putObject("metadata").put("shopupu_payment_id", "12").put("shopupu_order_id", "7").put("shopupu_refund_key", "old-operation");
        ((ObjectNode) event.path("data")).set("object", refund);
        String raw = event.toString();
        service.handle(raw, signature(raw));
        verify(payments).applyVerifiedRefundCallback("stripe", 12L, "old-operation", "evt_one", result);
    }

    private PaymentProviderSnapshot snapshot(String external, String refundKey) {
        return new PaymentProviderSnapshot("stripe", 7L, 12L, external, new BigDecimal("12.34"), "EUR", refundKey, null);
    }

    private ObjectNode event() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"id":"evt_one","livemode":false,"api_version":"2025-06-30.basil","type":"checkout.session.completed",
                 "data":{"object":{"object":"checkout.session","id":"cs_test_one","livemode":false,"mode":"payment",
                   "client_reference_id":"12","metadata":{"shopupu_order_id":"7","shopupu_payment_id":"12"},
                   "amount_total":1234,"currency":"eur","status":"complete","payment_status":"paid"}}}
                """);
    }

    private String signature(String raw) throws Exception {
        long time = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_fixture".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + time + ",v1=" + HexFormat.of().formatHex(mac.doFinal((time + "." + raw).getBytes(StandardCharsets.UTF_8)));
    }
}
