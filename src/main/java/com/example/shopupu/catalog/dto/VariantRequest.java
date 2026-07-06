package com.example.shopupu.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record VariantRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "SKU must contain letters, numbers, _ or -")
        String sku,

        @NotBlank
        @Size(max = 32)
        String size,

        @Size(max = 64)
        String color,

        @DecimalMin(value = "0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal price,

        @DecimalMin(value = "0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal oldPrice,

        @PositiveOrZero
        Integer stock,

        Boolean enabled
) {
}
