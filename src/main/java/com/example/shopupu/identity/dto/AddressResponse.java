package com.example.shopupu.identity.dto;

import com.example.shopupu.identity.entity.UserAddress;

public record AddressResponse(
        Long id,
        String fullName,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        boolean defaultAddress
) {
    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getFullName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefaultAddress()
        );
    }
}
