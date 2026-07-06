package com.example.shopupu.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 128) String fullName,
        @NotBlank @Size(max = 128) String line1,
        @Size(max = 128) String line2,
        @NotBlank @Size(max = 64) String city,
        @Size(max = 64) String state,
        @NotBlank @Size(max = 16) String postalCode,
        @NotBlank @Size(max = 64) String country,
        Boolean defaultAddress
) {
}
