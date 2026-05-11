package com.example.shopupu.reviews.dto;

import com.example.shopupu.reviews.entity.ReviewStatus;
import jakarta.validation.constraints.NotNull;

/**
 * describes the ReviewStatusRequest record.
 */
public record ReviewStatusRequest(
        @NotNull ReviewStatus status
) {
}
