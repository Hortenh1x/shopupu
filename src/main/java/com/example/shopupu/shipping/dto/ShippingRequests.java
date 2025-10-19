package com.example.shopupu.shipping.dto;

import com.example.shopupu.shipping.entity.ShippingMethod;

public class ShippingRequests {

    public record SetAddress(
            Long orderId,
            String fullName,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country
    ) {}

    public record SetMethod(
            Long orderId,
            ShippingMethod method
    ) {}
}

