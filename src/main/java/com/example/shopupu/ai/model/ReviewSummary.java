package com.example.shopupu.ai.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Structured LLM output for "what customers say" (the Jackson descriptions
 * feed the structured-output JSON schema sent to Claude).
 */
@JsonClassDescription("Summary of customer reviews for one product")
public record ReviewSummary(
        @JsonPropertyDescription("2-3 sentence overall summary in the dominant language of the reviews")
        String tldr,
        @JsonPropertyDescription("Up to 5 short positive points customers repeat")
        List<String> pros,
        @JsonPropertyDescription("Up to 5 short negative points customers repeat; empty if none")
        List<String> cons,
        @JsonPropertyDescription("Overall sentiment across the reviews")
        Sentiment sentiment
) {

    public enum Sentiment {
        POSITIVE,
        MIXED,
        NEGATIVE
    }
}
