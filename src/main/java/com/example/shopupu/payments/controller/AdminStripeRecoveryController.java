package com.example.shopupu.payments.controller;

import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest;
import com.example.shopupu.payments.gateway.StripePaymentGatewayClient;
import com.example.shopupu.payments.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator recovery reads a known test Session; it never creates a second checkout. */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "stripe")
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStripeRecoveryController {
    private final PaymentService payments;
    private final StripePaymentGatewayClient stripe;

    @PostMapping("/{id}/recover-stripe-session")
    public PaymentResponse recover(@PathVariable Long id, @Valid @RequestBody RecoveryRequest request) {
        var expected = payments.getProviderSnapshot(id);
        if (!"stripe".equals(expected.provider())) throw new ForbiddenOperationException("Wrong payment provider");
        if (expected.externalPaymentId() != null && !expected.externalPaymentId().equals(request.sessionId())) {
            throw new BadRequestException("A different Stripe session is already bound to this payment");
        }
        // GET validates test mode, provider metadata, exact amount, currency and local payment ID.
        // Both DB calls own their transactions; the provider read is outside them.
        var result = stripe.recoverKnownSession(new PaymentGatewayStatusRequest(expected.orderId(), id,
                request.sessionId(), expected.amount(), expected.currency()));
        return payments.applyVerifiedSessionRecovery(id, result);
    }

    public record RecoveryRequest(@NotBlank @Pattern(regexp = "cs_test_[A-Za-z0-9]+") @Size(max = 80) String sessionId) {}
}
