package com.example.shopupu.ai.service;

import com.example.shopupu.ai.model.ParsedProductQuery;
import com.example.shopupu.ai.repository.ProductEmbeddingRepository;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.service.ProductQueryService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Read side of semantic search. The embedding call happens before any DB
 * transaction (ADR-0003); entity->DTO mapping happens inside ProductQueryService's
 * read-only transaction (OSIV off). Any AI failure falls back to the existing
 * keyword search — search must keep working with no provider at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final com.example.shopupu.config.AiProperties aiProperties;
    private final QueryEmbeddingService queryEmbeddingService;
    private final NlQueryParser nlQueryParser;
    private final ProductEmbeddingRepository embeddingRepository;
    private final ProductQueryService productQueryService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public List<ProductListItem> semanticSearch(String q, int limit) {
        String query = normalize(q);
        if (query.isBlank()) {
            return List.of();
        }
        if (aiProperties.isEnabled()) {
            try {
                float[] embedding = queryEmbeddingService.embedQuery(query);
                List<Long> ids = embeddingRepository.findNearestProductIds(
                        embedding, aiProperties.getEmbeddingModel(), limit);
                if (!ids.isEmpty()) {
                    meterRegistry.counter("shopupu.ai", "op", "semantic_search", "result", "ok").increment();
                    return productQueryService.findListItemsByIds(ids);
                }
            } catch (Exception ex) {
                log.warn("Semantic search failed, falling back to keyword search", ex);
            }
        }
        meterRegistry.counter("shopupu.ai", "op", "semantic_search", "result", "fallback").increment();
        return keywordFallback(q, limit);
    }

    /**
     * Semantic search that keeps the cosine distance per hit so callers (the
     * stylist) can judge whether the closest product is actually the requested
     * garment. Distance is null on the keyword fallback — relevance unknown.
     */
    public List<ScoredItem> semanticSearchScored(String q, int limit) {
        String query = normalize(q);
        if (query.isBlank()) {
            return List.of();
        }
        if (aiProperties.isEnabled()) {
            try {
                float[] embedding = queryEmbeddingService.embedQuery(query);
                var scored = embeddingRepository.findNearestProductIdsWithDistance(
                        embedding, aiProperties.getEmbeddingModel(), limit);
                if (!scored.isEmpty()) {
                    meterRegistry.counter("shopupu.ai", "op", "semantic_search", "result", "ok").increment();
                    var distanceById = scored.stream().collect(java.util.stream.Collectors.toMap(
                            com.example.shopupu.ai.repository.ProductEmbeddingRepository.ScoredProductId::productId,
                            com.example.shopupu.ai.repository.ProductEmbeddingRepository.ScoredProductId::distance));
                    return productQueryService.findListItemsByIds(scored.stream()
                                    .map(com.example.shopupu.ai.repository.ProductEmbeddingRepository.ScoredProductId::productId)
                                    .toList())
                            .stream()
                            .map(item -> new ScoredItem(item, distanceById.get(item.id())))
                            .toList();
                }
            } catch (Exception ex) {
                log.warn("Scored semantic search failed, falling back to keyword search", ex);
            }
        }
        meterRegistry.counter("shopupu.ai", "op", "semantic_search", "result", "fallback").increment();
        return keywordFallback(q, limit).stream().map(item -> new ScoredItem(item, null)).toList();
    }

    /** A search hit plus its cosine distance (null when relevance is unknown). */
    public record ScoredItem(ProductListItem item, Double distance) {
    }

    /** Natural-language query -> ProductFilter (Claude structured output) -> existing search. */
    public Page<ProductListItem> nlSearch(String q, Pageable pageable) {
        ProductFilter filter = new ProductFilter();
        filter.enabled = Boolean.TRUE;
        filter.q = q;
        if (aiProperties.isEnabled()) {
            nlQueryParser.parse(normalize(q)).ifPresent(parsed -> apply(parsed, filter, q));
        }
        return productQueryService.findProducts(filter, pageable);
    }

    private void apply(ParsedProductQuery parsed, ProductFilter filter, String originalQuery) {
        filter.q = parsed.q() == null || parsed.q().isBlank() ? originalQuery : parsed.q();
        filter.gender = parsed.gender();
        filter.size = parsed.size();
        filter.color = parsed.color();
        filter.minPrice = parsed.minPrice();
        filter.maxPrice = parsed.maxPrice();
    }

    private List<ProductListItem> keywordFallback(String q, int limit) {
        ProductFilter filter = new ProductFilter();
        filter.q = q;
        filter.enabled = Boolean.TRUE;
        return productQueryService.findProducts(filter, PageRequest.of(0, limit)).getContent();
    }

    private String normalize(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }
}
