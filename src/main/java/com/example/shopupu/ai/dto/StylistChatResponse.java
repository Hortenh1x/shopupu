package com.example.shopupu.ai.dto;

import com.example.shopupu.catalog.dto.ProductListItem;
import java.util.List;

/**
 * Stylist answer: a short reply plus outfit slots resolved to real products.
 * {@code unavailable} honestly names requested garments the catalog does not
 * carry (declared by the LLM plan or caught by the relevance gate).
 * {@code degraded} is true when the LLM was unavailable and the keyword
 * fallback produced the plan.
 */
public record StylistChatResponse(
        String reply,
        List<StylistSlot> slots,
        List<String> unavailable,
        boolean degraded
) {

    public record StylistSlot(String slot, List<ProductListItem> products) {
    }
}
