package com.example.shopupu.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * describes the SetShippingAddressRequest record.
 */
public record SetShippingAddressRequest(
        @NotNull
        Long orderId,

        @NotBlank
        @Size(max = 128)
        String fullName,

        @NotBlank
        @Size(max = 128)
        String line1,

        @Size(max = 128)
        String line2,

        @NotBlank
        @Size(max = 64)
        String city,

        @NotBlank
        @Size(max = 64)
        String state,

        @NotBlank
        @Size(max = 16)
        String postalCode,

        @NotBlank
        @Size(max = 64)
        String country
) {
}