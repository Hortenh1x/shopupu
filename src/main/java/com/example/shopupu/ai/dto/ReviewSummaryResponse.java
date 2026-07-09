package com.example.shopupu.ai.dto;

import java.time.Instant;
import java.util.List;

public record ReviewSummaryResponse(
        Long productId,
        String tldr,
        List<String> pros,
        List<String> cons,
        String sentiment,
        int basedOnReviews,
        Instant generatedAt
) {
}
