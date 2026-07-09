package com.example.shopupu.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.shopupu.ai.repository.ProductEmbeddingRepository;
import com.example.shopupu.ai.repository.ProductRecommendationRepository;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.entity.Gender;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.catalog.service.ProductQueryService;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.config.AiProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * describes the RecommendationServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private ProductEmbeddingRepository embeddingRepository;

    @Mock
    private ProductRecommendationRepository recommendationRepository;

    @Mock
    private ProductQueryService productQueryService;

    @Mock
    private ProductRepository productRepository;

    private AiProperties aiProperties;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        service = new RecommendationService(aiProperties, embeddingRepository,
                recommendationRepository, productQueryService, productRepository);
    }

    @Test
    void similarRequiresExistingProduct() {
        when(productRepository.existsById(9L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.findSimilar(9L, 8));
    }

    @Test
    void similarReturnsKnnNeighbours() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(embeddingRepository.findSimilarProductIds(1L, aiProperties.getEmbeddingModel(), 8))
                .thenReturn(List.of(2L, 3L));
        List<ProductListItem> items = List.of(listItem(2L), listItem(3L));
        when(productQueryService.findListItemsByIds(List.of(2L, 3L))).thenReturn(items);

        assertEquals(items, service.findSimilar(1L, 8));
    }

    @Test
    void similarIsEmptyWhenAiIsDisabled() {
        aiProperties.setEnabled(false);
        when(productRepository.existsById(1L)).thenReturn(true);

        assertEquals(List.of(), service.findSimilar(1L, 8));
        verifyNoInteractions(embeddingRepository);
    }

    @Test
    void similarDegradesToEmptyOnFailure() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(embeddingRepository.findSimilarProductIds(anyLong(), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("down"));

        assertEquals(List.of(), service.findSimilar(1L, 8));
    }

    @Test
    void boughtTogetherServesPrecomputedPairs() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(recommendationRepository.findRecommendedProductIds(
                1L, ProductRecommendationRepository.KIND_BOUGHT_TOGETHER, 8))
                .thenReturn(List.of(5L));
        List<ProductListItem> items = List.of(listItem(5L));
        when(productQueryService.findListItemsByIds(List.of(5L))).thenReturn(items);

        assertEquals(items, service.findBoughtTogether(1L, 8));
    }

    @Test
    void boughtTogetherFallsBackToPopularInCategory() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(recommendationRepository.findRecommendedProductIds(anyLong(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(recommendationRepository.findPopularInSameCategory(1L, 8)).thenReturn(List.of(4L));
        List<ProductListItem> items = List.of(listItem(4L));
        when(productQueryService.findListItemsByIds(List.of(4L))).thenReturn(items);

        assertEquals(items, service.findBoughtTogether(1L, 8));
    }

    private ProductListItem listItem(Long id) {
        return new ProductListItem(id, "p" + id, "p-" + id, BigDecimal.ONE, null,
                null, Gender.UNISEX, true, null, null, null, null, null);
    }
}
