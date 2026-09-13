package com.example.shopupu.payments.service;

import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.payments.dto.PaymentCallbackRequest;
import com.example.shopupu.payments.dto.PaymentProviderSnapshot;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.gateway.PaymentGatewayRefundRequest;
import com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest;
import com.example.shopupu.payments.gateway.StripePaymentGatewayClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "stripe")
public class StripeWebhookService {
    private final PaymentProperties properties;
    private final ObjectMapper mapper;
    private final PaymentService payments;
    private final StripePaymentGatewayClient stripe;

    @Transactional(propagation = Propagation.NEVER)
    public void handle(String rawPayload, String signature) {
        if (!StripeWebhookSignature.isValid(properties.getStripe().getWebhookSecret(), rawPayload, signature, Clock.systemUTC())) {
            throw new ForbiddenOperationException("Invalid Stripe webhook signature");
        }
        JsonNode event;
        try {
            event = mapper.readTree(rawPayload);
        } catch (java.io.IOException ex) {
            throw new BadRequestException("Malformed Stripe event");
        }
        StripePaymentGatewayClient.requireTestObject(event);
        if (!StripePaymentGatewayClient.API_VERSION.equals(event.path("api_version").asText())) {
            throw new BadRequestException("Stripe webhook API version does not match the configured adapter");
        }
        String eventId = event.path("id").asText();
        if (!eventId.matches("evt_[A-Za-z0-9]+") || eventId.length() > 128) throw new BadRequestException("Invalid Stripe event ID");
        String type = event.path("type").asText();
        boolean checkoutEvent = type.equals("checkout.session.completed") || type.equals("checkout.session.async_payment_succeeded")
                || type.equals("checkout.session.async_payment_failed") || type.equals("checkout.session.expired");
        boolean refundEvent = type.equals("refund.created") || type.equals("refund.updated") || type.equals("refund.failed");
        if (!checkoutEvent && !refundEvent) return;
        JsonNode object = event.path("data").path("object");
        long paymentId;
        try {
            paymentId = Long.parseLong(object.path("metadata").path("shopupu_payment_id").asText());
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Missing Stripe payment reference");
        }
        PaymentProviderSnapshot expected = payments.getProviderSnapshot(paymentId);
        if (!"stripe".equals(expected.provider())) throw new ForbiddenOperationException("Wrong payment provider");
        if (checkoutEvent) {
            String externalId = expected.externalPaymentId() == null ? object.path("id").asText() : expected.externalPaymentId();
            StripePaymentGatewayClient.validateSession(object, new PaymentGatewayStatusRequest(expected.orderId(), paymentId,
                    externalId, expected.amount(), expected.currency()));
            PaymentStatus status = checkoutStatus(type, object);
            if (status != null) payments.applyVerifiedCallback("stripe", new PaymentCallbackRequest(eventId, externalId, status,
                    "Verified Stripe test event: " + type, paymentId));
        } else {
            if (expected.refundOperationKey() == null) throw new BadRequestException("Refund was not prepared by this application");
            String eventOperationKey = object.path("metadata").path("shopupu_refund_key").asText();
            if (eventOperationKey.isBlank() || eventOperationKey.length() > 64) throw new BadRequestException("Missing refund operation reference");
            var request = new PaymentGatewayRefundRequest(expected.orderId(), paymentId, expected.externalPaymentId(),
                    expected.amount(), expected.currency(), eventOperationKey);
            // Signature is verified; fetch/validate the bound session and intent outside every DB transaction.
            var outcome = stripe.verifyRefundEvent(object, request);
            payments.applyVerifiedRefundCallback("stripe", paymentId, eventOperationKey, eventId, outcome);
        }
    }

    private PaymentStatus checkoutStatus(String type, JsonNode object) {
        String paid = object.path("payment_status").asText();
        return switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                    "paid".equals(paid) ? PaymentStatus.SUCCEEDED : null;
            case "checkout.session.expired" -> "expired".equals(object.path("status").asText()) && "unpaid".equals(paid)
                    ? PaymentStatus.EXPIRED : null;
            case "checkout.session.async_payment_failed" -> "unpaid".equals(paid) ? PaymentStatus.FAILED : null;
            default -> null;
        };
    }
}
