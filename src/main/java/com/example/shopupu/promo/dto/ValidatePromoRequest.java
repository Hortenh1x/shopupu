package com.example.shopupu.promo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ValidatePromoRequest(
        @NotBlank @Size(max = 64) String code
) {
}
