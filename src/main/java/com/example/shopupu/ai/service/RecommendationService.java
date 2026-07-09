package com.example.shopupu.ai.service;

import com.example.shopupu.ai.repository.ProductEmbeddingRepository;
import com.example.shopupu.ai.repository.ProductRecommendationRepository;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.catalog.service.ProductQueryService;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.config.AiProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Product-page recommendations: "similar" is KNN over the semantic-search
 * embeddings; "bought together" is precomputed SQL co-occurrence with a
 * popular-in-category fallback. Both endpoints must answer even with AI off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final AiProperties aiProperties;
    private final ProductEmbeddingRepository embeddingRepository;
    private final ProductRecommendationRepository recommendationRepository;
    private final ProductQueryService productQueryService;
    private final ProductRepository productRepository;

    public List<ProductListItem> findSimilar(Long productId, int limit) {
        requireProduct(productId);
        if (!aiProperties.isEnabled()) {
            return List.of();
        }
        try {
            List<Long> ids = embeddingRepository.findSimilarProductIds(
                    productId, aiProperties.getEmbeddingModel(), limit);
            return productQueryService.findListItemsByIds(ids);
        } catch (Exception ex) {
            log.warn("Similar-products lookup failed for product {}", productId, ex);
            return List.of();
        }
    }

    @Cacheable(cacheNames = "recommendations", key = "#productId + ':' + #limit")
    public List<ProductListItem> findBoughtTogether(Long productId, int limit) {
        requireProduct(productId);
        List<Long> ids = recommendationRepository.findRecommendedProductIds(
                productId, ProductRecommendationRepository.KIND_BOUGHT_TOGETHER, limit);
        if (ids.isEmpty()) {
            ids = recommendationRepository.findPopularInSameCategory(productId, limit);
        }
        return productQueryService.findListItemsByIds(ids);
    }

    private void requireProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product with id " + productId + " not found");
        }
    }
}
