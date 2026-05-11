package com.example.shopupu.payments.mapper;

import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.entity.Payment;
import org.springframework.stereotype.Component;


@Component
/**
 * describes the PaymentMapper class.
 */
public class PaymentMapper {


    // handles toResponse.
    public PaymentResponse toResponse(Payment payment) {
        if (payment == null) return null;
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getExternalId(),
                payment.getProvider(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentUrl(),
                payment.getClientToken(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}