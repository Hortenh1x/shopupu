package com.example.shopupu.shipping.dto;

import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.shipping.entity.ShippingMethod;
import com.example.shopupu.shipping.entity.ShippingStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * describes the ShipmentDto record.
 */
public record ShipmentDto(
        Long orderId,
        ShippingMethod method,
        ShippingStatus shippingStatus,
        OrderStatus orderStatus,
        BigDecimal shippingCost,
        String currency,
        String trackingNumber,
        ShippingAddressDto address,
        Instant createdAt,
        Instant updatedAt
) {}
