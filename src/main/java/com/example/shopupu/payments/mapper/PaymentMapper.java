package com.example.shopupu.payments.mapper;

import com.example.shopupu.payments.dto.PaymentDto;
import com.example.shopupu.payments.entity.Payment;

public final class PaymentMapper {
    private PaymentMapper(){}

    public static PaymentDto toDto(Payment p){
        return new PaymentDto(
                p.getId(),
                p.getOrder().getId(),
                p.getAmount(),
                p.getCurrency(),
                p.getProvider(),
                p.getStatus(),
                p.getProviderPaymentId(),
                p.getClientSecret(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
