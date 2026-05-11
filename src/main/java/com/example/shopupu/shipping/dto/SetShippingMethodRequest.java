package com.example.shopupu.shipping.dto;

import com.example.shopupu.shipping.entity.ShippingMethod;
import jakarta.validation.constraints.NotNull;

/**
 * describes the SetShippingMethodRequest record.
 */
public record SetShippingMethodRequest(
        @NotNull
        Long orderId,

        @NotNull
        ShippingMethod method
) {
}