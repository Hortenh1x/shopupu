package com.example.shopupu.payments.controller;

import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }
}
