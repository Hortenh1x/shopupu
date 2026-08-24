package com.example.shopupu.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.shopupu.ai.dto.StylistChatRequest;
import com.example.shopupu.ai.dto.StylistChatResponse;
import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.model.OutfitPlan;
import com.example.shopupu.ai.service.SemanticSearchService.ScoredItem;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.entity.Gender;
import com.example.shopupu.config.AiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StylistServiceTest {

    private static final double NEAR = 0.30;
    private static final double FAR = 0.55;

    @Mock
    private LlmClient llmClient;

    @Mock
    private SemanticSearchService semanticSearchService;

    private StylistService service;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = new AiProperties();
        service = new StylistService(aiProperties, llmClient, semanticSearchService, new SimpleMeterRegistry());
    }

    @Test
    void resolvesLlmPlanIntoProductSlots() {
        OutfitPlan plan = new OutfitPlan("Blazer and trousers.", List.of(
                new OutfitPlan.OutfitSlot("Blazer", "tailored wool blazer", null, null),
                new OutfitPlan.OutfitSlot("Trousers", "pleated trousers", null, null)), List.of());
        when(llmClient.planOutfit(any(), anyString())).thenReturn(Optional.of(plan));
        when(semanticSearchService.semanticSearchScored(eq("tailored wool blazer"), anyInt()))
                .thenReturn(List.of(scored(8L, "Tailored Wool Blazer", "184.00", Gender.MEN, NEAR)));
        when(semanticSearchService.semanticSearchScored(eq("pleated trousers"), anyInt()))
                .thenReturn(List.of(scored(12L, "Pleated Wide Trousers", "104.00", Gender.WOMEN, NEAR)));

        StylistChatResponse response = service.chat(new StylistChatRequest("business meeting tonight", null));

        assertEquals("Blazer and trousers.", response.reply());
        assertFalse(response.degraded());
        assertTrue(response.unavailable().isEmpty());
        assertEquals(2, response.slots().size());
        assertEquals("Blazer", response.slots().get(0).slot());
        assertEquals(8L, response.slots().get(0).products().get(0).id());
    }

    @Test
    void fallsBackToKeywordPlanWhenLlmUnavailable() {
        when(llmClient.planOutfit(any(), anyString())).thenReturn(Optional.empty());
        when(semanticSearchService.semanticSearchScored(eq("tailored wool blazer"), anyInt()))
                .thenReturn(List.of(scored(8L, "Tailored Wool Blazer", "184.00", Gender.MEN, NEAR)));
        when(semanticSearchService.semanticSearchScored(eq("pleated formal trousers"), anyInt()))
                .thenReturn(List.of(scored(12L, "Pleated Wide Trousers", "104.00", Gender.WOMEN, NEAR)));
        when(semanticSearchService.semanticSearchScored(eq("crisp oxford shirt"), anyInt()))
                .thenReturn(List.of(scored(17L, "Oversized Oxford Shirt", "76.00", Gender.UNISEX, NEAR)));

        StylistChatResponse response =
                service.chat(new StylistChatRequest("нужно что-то официальное на деловую встречу", null));

        assertTrue(response.degraded());
        // formal branch of the keyword planner: blazer + trousers + shirt
        assertEquals(3, response.slots().size());
        assertEquals("Blazer", response.slots().get(0).slot());
    }

    @Test
    void neverRecommendsTheSameProductInTwoSlots() {
        OutfitPlan plan = new OutfitPlan("Rain plan.", List.of(
                new OutfitPlan.OutfitSlot("Rain jacket", "rain jacket", null, null),
                new OutfitPlan.OutfitSlot("Trench coat", "trench coat", null, null)), List.of());
        when(llmClient.planOutfit(any(), anyString())).thenReturn(Optional.of(plan));
        ScoredItem rainJacket = scored(18L, "Technical Rain Jacket", "132.00", Gender.UNISEX, NEAR);
        when(semanticSearchService.semanticSearchScored(eq("rain jacket"), anyInt()))
                .thenReturn(List.of(rainJacket));
        // semantic search ranks the rain jacket first for the trench query too
        when(semanticSearchService.semanticSearchScored(eq("trench coat"), anyInt()))
                .thenReturn(List.of(rainJacket, scored(9L, "Lightweight Trench Coat", "158.00", Gender.WOMEN, NEAR)));

        StylistChatResponse response = service.chat(new StylistChatRequest("rainy day", null));

        assertEquals(2, response.slots().size());
        assertEquals(18L, response.slots().get(0).products().get(0).id());
        assertEquals(List.of(9L),
                response.slots().get(1).products().stream().map(ProductListItem::id).toList());
        // consumed by another slot is NOT "not in catalog"
        assertTrue(response.unavailable().isEmpty());
    }

    @Test
    void filtersByGenderKeepingUnisex() {
        OutfitPlan plan = new OutfitPlan("For him.", List.of(
                new OutfitPlan.OutfitSlot("Shirt", "shirt", Gender.MEN, null)), List.of());
        when(llmClient.planOutfit(any(), anyString())).thenReturn(Optional.of(plan));
        when(semanticSearchService.semanticSearchScored(eq("shirt"), anyInt())).thenReturn(List.of(
                scored(5L, "Urban Ribbed Tank", "29.00", Gender.WOMEN, NEAR),
                scored(17L, "Oversized Oxford Shirt", "76.00", Gender.UNISEX, NEAR),
                scored(4L, "Linen Resort Shirt", "68.00", Gender.MEN, NEAR)));

        StylistChatResponse response = service.chat(new StylistChatRequest("shirt for him", null));

        // women's tank filtered out; one product per slot keeps the top unisex/men match
        List<Long> ids = response.slots().get(0).products().stream().map(ProductListItem::id).toList();
        assertEquals(List.of(17L), ids);
    }

    @Test
    void reportsSlotAsUnavailableWhenNoHitIsActuallyThatGarment() {
        OutfitPlan plan = new OutfitPlan("Formal set with a tie.", List.of(
                new OutfitPlan.OutfitSlot("Blazer", "tailored wool blazer", null, null),
                new OutfitPlan.OutfitSlot("Галстук", "silk necktie tie", null, null)), List.of());
        when(llmClient.planOutfit(any(), anyString())).thenReturn(Optional.of(plan));
        when(semanticSearchService.semanticSearchScored(eq("tailored wool blazer"), anyInt()))
                .thenReturn(List.of(scored(8L, "Tailored Wool Blazer", "184.00", Gender.MEN, NEAR)));
        // nearest neighbours exist but none is a tie: all beyond the relevance gate
        when(semanticSearchService.semanticSearchScored(eq("silk necktie tie"), anyInt()))
                .thenReturn(List.of(
                        scored(6L, "Merino Crewneck Sweater", "96.00", Gender.UNISEX, FAR),
                        scored(5L, "Urban Ribbed Tank", "29.00", Gender.WOMEN, FAR)));

        StylistChatResponse response = service.chat(new StylistChatRequest("костюм с галстуком", null));

        assertEquals(1, response.slots().size());
        assertEquals("Blazer", response.slots().get(0).slot());
        assertEquals(List.of("Галстук"), response.unavailable());
    }

    @Test
    void mergesPlanDeclaredUnavailableWithGateFindings() {
        OutfitPlan plan = new OutfitPlan("No ties here, sorry.", List.of(
                new OutfitPlan.OutfitSlot("Shirt", "oxford shirt", null, null)),
                List.of("галстук"));
        when(llmClient.planOutfit(any(), anyString())).thenReturn(Optional.of(plan));
        when(semanticSearchService.semanticSearchScored(eq("oxford shirt"), anyInt()))
                .thenReturn(List.of(scored(17L, "Oversized Oxford Shirt", "76.00", Gender.UNISEX, NEAR)));

        StylistChatResponse response = service.chat(new StylistChatRequest("рубашка и галстук", null));

        assertEquals(List.of("галстук"), response.unavailable());
        assertEquals(1, response.slots().size());
    }

    @Test
    void keywordFallbackHitsWithoutDistancesAreTrusted() {
        OutfitPlan plan = new OutfitPlan("Plain plan.", List.of(
                new OutfitPlan.OutfitSlot("Shirt", "shirt", null, null)), List.of());
        when(llmClient.planOutfit(any(), anyString())).thenReturn(Optional.of(plan));
        // keyword fallback: no embeddings, distance unknown -> no honesty claims
        when(semanticSearchService.semanticSearchScored(eq("shirt"), anyInt()))
                .thenReturn(List.of(new ScoredItem(
                        item(17L, "Oversized Oxford Shirt", "76.00", Gender.UNISEX), null)));

        StylistChatResponse response = service.chat(new StylistChatRequest("shirt", null));

        assertEquals(1, response.slots().size());
        assertTrue(response.unavailable().isEmpty());
    }

    private ScoredItem scored(Long id, String title, String price, Gender gender, double distance) {
        return new ScoredItem(item(id, title, price, gender), distance);
    }

    private ProductListItem item(Long id, String title, String price, Gender gender) {
        return new ProductListItem(id, title, title.toLowerCase().replace(' ', '-'),
                new BigDecimal(price), null, "Brand", gender, true, null, 1L, "category", null, null);
    }
}
