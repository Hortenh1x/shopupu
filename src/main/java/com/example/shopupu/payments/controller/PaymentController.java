package com.example.shopupu.payments.controller;

import com.example.shopupu.payments.dto.PaymentDto;
import com.example.shopupu.payments.mapper.PaymentMapper;
import com.example.shopupu.payments.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RU: Контроллер платежей: создание intent, подтверждение (webhook/кнопка), отмена.
 * EN: Payments controller: create intent, confirm (webhook/button), cancel.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // RU: создать платёж (intent) по заказу
    // EN: create payment intent for an order
    public record CreatePaymentRequest(String provider, String currency) {}
    @PostMapping("/{orderId}")
    public ResponseEntity<PaymentDto> createPayment(@PathVariable Long orderId,
                                                    @RequestBody CreatePaymentRequest req){
        var p = paymentService.createPayment(orderId, req.provider(), req.currency());
        return ResponseEntity.ok(PaymentMapper.toDto(p));
    }

    // RU: подтверждение (например, после 3DS или колбэка)
    // EN: confirm (e.g., after 3DS or provider callback)
    public record ConfirmRequest(String providerPaymentId) {}
    @PostMapping("/confirm")
    public ResponseEntity<PaymentDto> confirmPayment(@RequestBody ConfirmRequest req){
        var p = paymentService.confirm(req.providerPaymentId());
        return ResponseEntity.ok(PaymentMapper.toDto(p));
    }

    // RU: отмена платежа
    // EN: cancel payment
    public record CancelRequest(String providerPaymentId) {}
    @PostMapping("/cancel")
    public ResponseEntity<PaymentDto> cancelPayment(@RequestBody CancelRequest req){
        var p = paymentService.cancel(req.providerPaymentId());
        return ResponseEntity.ok(PaymentMapper.toDto(p));
    }

    // (опционально) вебхук провайдера — тогда нужно .permitAll() в Security
    // (optionally) provider webhook — then add .permitAll() in Security
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook() {
        // парсим JSON провайдера, вызываем paymentService.confirm()/cancel() по событию
        return ResponseEntity.ok().build();
    }

}