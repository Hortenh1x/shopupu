package com.example.shopupu.catalog.dto;

import com.example.shopupu.catalog.entity.Gender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;


public record ProductRequest(
        @NotNull
        Long categoryId,

        @NotBlank
        @Size(max = 255)
        String title,

        @Size(max = 255)
        @Pattern(regexp = "^[a-z0-9-]*$", message = "Slug may contain lowercase letters, numbers and dashes")
        String slug,

        @Size(max = 5000)
        String description,

        @NotNull
        @DecimalMin(value = "0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal price,

        @DecimalMin(value = "0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal oldPrice,

        @Size(max = 255)
        String brandName,

        Gender gender,

        @Size(max = 32)
        String season,

        @Size(max = 255)
        String material,

        @Size(max = 5000)
        String careInstructions,

        @Size(max = 255)
        String metaTitle,

        @Size(max = 512)
        String metaDescription,

        Boolean enabled
) {
}
