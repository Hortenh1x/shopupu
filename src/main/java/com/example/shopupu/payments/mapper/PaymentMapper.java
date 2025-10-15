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
                payment.getStatus(),  // без вызова fromStripeStatus
                payment.getAmount(),
                payment.getClientSecret() // если у тебя есть такое поле
        );
    }
}
