package com.example.shopupu.identity.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 128) String firstName,
        @Size(max = 128) String lastName,
        @Size(max = 32) @Pattern(regexp = "^[+0-9()\\- ]*$", message = "Invalid phone number") String phone,
        @Size(max = 32) String preferredSize
) {
}
