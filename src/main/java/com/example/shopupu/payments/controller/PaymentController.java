package com.example.shopupu.payments.controller;

import com.example.shopupu.payments.dto.CreatePaymentRequest;
import com.example.shopupu.payments.dto.PaymentCallbackRequest;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentResponse payment = paymentService.createPayment(request.orderId(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentForCurrentUser(id));
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature
    ) throws com.fasterxml.jackson.core.JacksonException {
        PaymentCallbackRequest request = objectMapper.readValue(rawPayload, PaymentCallbackRequest.class);
        paymentService.handleCallback(request, rawPayload, signature);
        return ResponseEntity.noContent().build();
    }
}
