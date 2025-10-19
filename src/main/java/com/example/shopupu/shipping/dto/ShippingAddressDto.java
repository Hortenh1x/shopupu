package com.example.shopupu.shipping.dto;

public record ShippingAddressDto(
        Long id,
        String fullName,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country
) {}

