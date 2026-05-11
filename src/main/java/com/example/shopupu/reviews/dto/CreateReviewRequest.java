package com.example.shopupu.reviews.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * describes the CreateReviewRequest record.
 */
public record CreateReviewRequest(
        @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 5000) String body,
        Long orderId
) {
}
