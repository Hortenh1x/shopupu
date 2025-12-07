package com.example.shopupu.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating or updating category data.
 */
public record CategoryRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain lowercase letters, digits or '-' only")
        String slug,

        @Size(max = 4096)
        String description,

        Long parentId
) {
}