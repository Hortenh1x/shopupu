package com.example.shopupu.ai.model;

import com.example.shopupu.catalog.entity.Gender;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Structured LLM output for natural-language catalog queries; maps 1:1 onto
 * the attribute filters of {@code ProductFilter} (unknown fields stay null).
 */
@JsonClassDescription("Search filters extracted from a shopper's free-text clothing query")
public record ParsedProductQuery(
        @JsonPropertyDescription("Residual search keywords (garment type, style, fabric) in the query's own language, "
                + "without the attributes extracted into the other fields")
        String q,
        @JsonPropertyDescription("Target audience, only if explicitly stated")
        Gender gender,
        @JsonPropertyDescription("Clothing size if stated, e.g. S, M, L, XL, 42")
        String size,
        @JsonPropertyDescription("Color name translated to English, e.g. black, red")
        String color,
        @JsonPropertyDescription("Minimum price if stated, plain number in shop currency")
        BigDecimal minPrice,
        @JsonPropertyDescription("Maximum price if stated (e.g. 'under 100' / 'до 100' means 100), plain number")
        BigDecimal maxPrice
) {
}
