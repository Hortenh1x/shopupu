package com.example.shopupu.payments.mapper;

import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PaymentMapper {

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "externalPaymentId", source = "externalId")
    PaymentResponse toResponse(Payment payment);
}
