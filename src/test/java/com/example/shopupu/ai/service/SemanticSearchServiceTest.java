package com.example.shopupu.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.shopupu.ai.model.ParsedProductQuery;
import com.example.shopupu.ai.repository.ProductEmbeddingRepository;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.entity.Gender;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.service.ProductQueryService;
import com.example.shopupu.config.AiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * describes the SemanticSearchServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    @Mock
    private QueryEmbeddingService queryEmbeddingService;

    @Mock
    private NlQueryParser nlQueryParser;

    @Mock
    private ProductEmbeddingRepository embeddingRepository;

    @Mock
    private ProductQueryService productQueryService;

    private AiProperties aiProperties;
    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        service = new SemanticSearchService(aiProperties, queryEmbeddingService, nlQueryParser,
                embeddingRepository, productQueryService, new SimpleMeterRegistry());
    }

    @Test
    void semanticSearchReturnsKnnMatchesInRelevanceOrder() {
        float[] vector = {1, 0};
        when(queryEmbeddingService.embedQuery("red dress")).thenReturn(vector);
        when(embeddingRepository.findNearestProductIds(vector, aiProperties.getEmbeddingModel(), 10))
                .thenReturn(List.of(7L, 3L));
        List<ProductListItem> items = List.of(listItem(7L), listItem(3L));
        when(productQueryService.findListItemsByIds(List.of(7L, 3L))).thenReturn(items);

        assertEquals(items, service.semanticSearch("Red Dress", 10));
    }

    @Test
    void semanticSearchFallsBackToKeywordSearchWhenDisabled() {
        aiProperties.setEnabled(false);
        when(productQueryService.findProducts(any(), eq(PageRequest.of(0, 5))))
                .thenReturn(new PageImpl<>(List.of(listItem(1L))));

        List<ProductListItem> items = service.semanticSearch("dress", 5);

        assertEquals(1, items.size());
        verifyNoInteractions(queryEmbeddingService, embeddingRepository);
    }

    @Test
    void semanticSearchFallsBackWhenEmbeddingProviderFails() {
        when(queryEmbeddingService.embedQuery(anyString())).thenThrow(new IllegalStateException("down"));
        when(productQueryService.findProducts(any(), any())).thenReturn(new PageImpl<>(List.of(listItem(1L))));

        assertEquals(1, service.semanticSearch("dress", 5).size());
    }

    @Test
    void semanticSearchReturnsNothingForBlankQuery() {
        assertEquals(List.of(), service.semanticSearch("   ", 5));
        verifyNoInteractions(queryEmbeddingService, embeddingRepository, productQueryService);
    }

    @Test
    void nlSearchAppliesParsedFiltersToTheExistingSearch() {
        when(nlQueryParser.parse("тёплая куртка до 100"))
                .thenReturn(Optional.of(new ParsedProductQuery(
                        "куртка", Gender.WOMEN, null, "black", null, new BigDecimal("100"))));
        when(productQueryService.findProducts(any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.nlSearch("Тёплая куртка до 100", PageRequest.of(0, 20));

        ArgumentCaptor<ProductFilter> captor = ArgumentCaptor.forClass(ProductFilter.class);
        verify(productQueryService).findProducts(captor.capture(), any());
        ProductFilter filter = captor.getValue();
        assertEquals("куртка", filter.q);
        assertEquals(Gender.WOMEN, filter.gender);
        assertEquals("black", filter.color);
        assertEquals(new BigDecimal("100"), filter.maxPrice);
        assertEquals(Boolean.TRUE, filter.enabled);
    }

    @Test
    void nlSearchKeepsOriginalQueryWhenParserIsUnavailable() {
        when(nlQueryParser.parse(anyString())).thenReturn(Optional.empty());
        when(productQueryService.findProducts(any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.nlSearch("blue jeans", PageRequest.of(0, 20));

        ArgumentCaptor<ProductFilter> captor = ArgumentCaptor.forClass(ProductFilter.class);
        verify(productQueryService).findProducts(captor.capture(), any());
        assertEquals("blue jeans", captor.getValue().q);
    }

    private ProductListItem listItem(Long id) {
        return new ProductListItem(id, "p" + id, "p-" + id, BigDecimal.ONE, null,
                null, Gender.UNISEX, true, null, null, null, null, null);
    }
}
