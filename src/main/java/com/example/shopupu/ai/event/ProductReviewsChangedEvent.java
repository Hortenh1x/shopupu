package com.example.shopupu.ai.event;

/**
 * Published by ReviewService whenever the set of APPROVED reviews of a product
 * changes (moderation, edit, deletion); consumed AFTER_COMMIT to regenerate
 * the review summary.
 */
public record ProductReviewsChangedEvent(Long productId) {
}
