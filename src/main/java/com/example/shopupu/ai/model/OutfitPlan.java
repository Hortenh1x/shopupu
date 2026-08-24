package com.example.shopupu.ai.model;

import com.example.shopupu.catalog.entity.Gender;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.util.List;

/**
 * Structured LLM output for the stylist chat: a short reply for the shopper
 * plus outfit slots, each resolved against the catalog via semantic search.
 */
@JsonClassDescription("Outfit recommendation plan for a shopper's free-text request")
public record OutfitPlan(
        @JsonPropertyDescription("1-2 friendly sentences in the shopper's language explaining the outfit")
        String reply,
        @JsonPropertyDescription("2-4 outfit slots, one per garment")
        List<OutfitSlot> slots,
        @JsonPropertyDescription("Garments the shopper asked for that the shop does not carry, "
                + "in the shopper's own words; empty if everything is available")
        List<String> unavailable
) {

    public record OutfitSlot(
            @JsonPropertyDescription("Short garment label in the shopper's language, e.g. 'Пиджак' or 'Blazer'")
            String slot,
            @JsonPropertyDescription("English search keywords for this garment, e.g. 'tailored wool blazer'")
            String query,
            @JsonPropertyDescription("Target audience if implied by the request, else null")
            Gender gender,
            @JsonPropertyDescription("Maximum price per item if the shopper stated a budget, else null")
            BigDecimal maxPrice
    ) {
    }
}
