package com.example.shopupu.ai.dto;

import com.example.shopupu.catalog.dto.ProductListItem;
import java.util.List;

/**
 * Stylist answer: a short reply plus outfit slots resolved to real products.
 * {@code unavailable} honestly names requested garments the catalog does not
 * carry or cannot offer within the shopper's constraints.
 * {@code degraded} is true when AI is disabled, stubbed or unavailable and a
 * deterministic keyword plan and database-only lookup produced the answer.
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
