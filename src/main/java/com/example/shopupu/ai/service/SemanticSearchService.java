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

    /**
     * Natural-language search, hybrid: the LLM splits the query into attributes
     * (price, gender, size, colour) plus residual keywords, the residual keywords
     * are matched by embedding rather than by SQL LIKE, and the attributes filter
     * the candidates in the database. That is what makes "warm jacket under 120"
     * work — "warm" describes no product title, so the LIKE-only path found nothing.
     * Any miss (AI off, no embeddings, provider down, nothing relevant enough)
     * falls back to the keyword search, which is still the whole feature's floor.
     */
    public Page<ProductListItem> nlSearch(String q, Pageable pageable) {
        ProductFilter filter = new ProductFilter();
        filter.enabled = Boolean.TRUE;
        filter.q = q;
        if (aiProperties.isEnabled()) {
            nlQueryParser.parse(normalize(q)).ifPresent(parsed -> apply(parsed, filter, q));
            Page<ProductListItem> semantic = semanticPage(filter, pageable);
            if (semantic != null) {
                meterRegistry.counter("shopupu.ai", "op", "nl_search", "result", "ok").increment();
                return semantic;
            }
        }
        meterRegistry.counter("shopupu.ai", "op", "nl_search", "result", "fallback").increment();
        return productQueryService.findProducts(filter, pageable);
    }

    /** Vector candidates for the residual keywords, filtered by the parsed attributes; null = no usable result. */
    private Page<ProductListItem> semanticPage(ProductFilter filter, Pageable pageable) {
        String keywords = normalize(filter.q);
        if (keywords.isBlank()) {
            return null;
        }
        try {
            float[] embedding = queryEmbeddingService.embedQuery(keywords);
            List<Long> ids = embeddingRepository
                    .findNearestProductIdsWithDistance(
                            embedding, aiProperties.getEmbeddingModel(), aiProperties.getNlSearchCandidates())
                    .stream()
                    .filter(scored -> scored.distance() <= aiProperties.getNlSearchMaxDistance())
                    .map(ProductEmbeddingRepository.ScoredProductId::productId)
                    .toList();
            if (ids.isEmpty()) {
                return null;
            }
            // q is dropped on purpose: the embedding already matched the keywords
            List<ProductListItem> matches = productQueryService.findListItemsByIdsMatching(ids, attributesOf(filter));
            return matches.isEmpty() ? null : pageOf(matches, pageable);
        } catch (Exception ex) {
            log.warn("NL semantic search failed, falling back to keyword search", ex);
            return null;
        }
    }

    private ProductFilter attributesOf(ProductFilter filter) {
        ProductFilter scoped = new ProductFilter();
        scoped.enabled = filter.enabled;
        scoped.categoryId = filter.categoryId;
        scoped.brandId = filter.brandId;
        scoped.gender = filter.gender;
        scoped.size = filter.size;
        scoped.color = filter.color;
        scoped.minPrice = filter.minPrice;
        scoped.maxPrice = filter.maxPrice;
        scoped.inStock = filter.inStock;
        return scoped;
    }

    /** Pages the already-ranked list in memory; relevance order wins over the pageable's sort. */
    private Page<ProductListItem> pageOf(List<ProductListItem> items, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new org.springframework.data.domain.PageImpl<>(items);
        }
        int from = (int) Math.min(pageable.getOffset(), items.size());
        int to = Math.min(from + pageable.getPageSize(), items.size());
        return new org.springframework.data.domain.PageImpl<>(items.subList(from, to), pageable, items.size());
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
