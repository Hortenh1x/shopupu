package com.example.shopupu.payments.mapper;

import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentStatus;
import org.springframework.stereotype.Component;

/**
 * RU: Конвертер между сущностями платежей и DTO.
 * EN: Mapper between payment entities and DTOs.
 */
@Component
public class PaymentMapper {

    /**
     * RU: Преобразует Payment в PaymentResponse.
     * EN: Maps Payment entity to DTO.
     */
    public PaymentResponse toResponse(Payment payment) {
        if (payment == null) return null;

        return new PaymentResponse(
                payment.getExternalId(),
                payment.getProvider(),
                PaymentStatus.fromStripeStatus(payment.getStatus().name()),
                payment.getAmount()
        );
    }
}
