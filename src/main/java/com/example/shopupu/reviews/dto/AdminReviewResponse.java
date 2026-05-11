package com.example.shopupu.reviews.dto;

import com.example.shopupu.reviews.entity.ReviewStatus;

import java.time.Instant;

/**
 * describes the AdminReviewResponse record.
 */
public record AdminReviewResponse(
        Long id,
        Long productId,
        String productTitle,
        Long userId,
        String userEmail,
        Integer rating,
        String title,
        String body,
        ReviewStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
