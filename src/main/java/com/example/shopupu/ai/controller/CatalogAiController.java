package com.example.shopupu.ai.controller;

import com.example.shopupu.ai.dto.ReviewSummaryResponse;
import com.example.shopupu.ai.service.RecommendationService;
import com.example.shopupu.ai.service.ReviewSummaryService;
import com.example.shopupu.ai.service.SemanticSearchService;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.common.exception.BadRequestException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public AI-assisted catalog reads. Everything lives under GET
 * /api/v1/catalog/** (already whitelisted in SecurityConfig) and degrades to
 * non-AI behaviour when providers are absent — the shop never breaks.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog/products")
public class CatalogAiController {

    private static final int MAX_LIMIT = 50;

    private final SemanticSearchService semanticSearchService;
    private final RecommendationService recommendationService;
    private final ReviewSummaryService reviewSummaryService;

    @GetMapping("/semantic-search")
    public List<ProductListItem> semanticSearch(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return semanticSearchService.semanticSearch(requireQuery(q), clampLimit(limit));
    }

    @GetMapping("/nl-search")
    public Page<ProductListItem> nlSearch(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return semanticSearchService.nlSearch(requireQuery(q), pageable);
    }

    @GetMapping("/{id}/similar")
    public List<ProductListItem> similar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return recommendationService.findSimilar(id, clampLimit(limit));
    }

    @GetMapping("/{id}/bought-together")
    public List<ProductListItem> boughtTogether(
            @PathVariable Long id,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return recommendationService.findBoughtTogether(id, clampLimit(limit));
    }

    @GetMapping("/{id}/review-summary")
    public ReviewSummaryResponse reviewSummary(@PathVariable Long id) {
        return reviewSummaryService.getSummary(id);
    }

    private String requireQuery(String q) {
        if (q == null || q.isBlank()) {
            throw new BadRequestException("Query parameter 'q' must not be blank");
        }
        return q;
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
