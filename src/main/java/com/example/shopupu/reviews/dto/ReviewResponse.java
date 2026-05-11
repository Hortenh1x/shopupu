package com.example.shopupu.reviews.dto;

import com.example.shopupu.reviews.entity.ReviewStatus;

import java.time.Instant;

/**
 * describes the ReviewResponse record.
 */
public record ReviewResponse(
        Long id,
        Long productId,
        Long userId,
        String username,
        Integer rating,
        String title,
        String body,
        ReviewStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
