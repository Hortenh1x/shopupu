package com.example.shopupu.ai.service;

import com.example.shopupu.ai.dto.StylistChatRequest;
import com.example.shopupu.ai.dto.StylistChatResponse;
import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.gateway.StubLlmClient;
import com.example.shopupu.ai.model.ChatMessage;
import com.example.shopupu.ai.model.OutfitPlan;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.entity.Gender;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stylist chat: LLM turns the conversation into an outfit plan, then each slot
 * is resolved to catalog products via semantic search. Follows ADR-0003 — the
 * LLM call happens before any DB work (SemanticSearchService owns its own
 * read-only transactions). Every AI failure degrades to the keyword planner:
 * the bot always answers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StylistService {

    private static final int MAX_SLOTS = 4;
    // one precise match per slot: a second "alternative" out of a small catalog
    // tends to be a different garment entirely and starves the next slot via dedupe
    private static final int PRODUCTS_PER_SLOT = 1;
    private static final int CANDIDATES_PER_SLOT = 6;

    /** What the shop carries, so the LLM never plans unresolvable slots. */
    static final String CATALOG_CONTEXT = """
            hoodies (pullover, zip); shirts & tops (linen resort shirt, ribbed tank, \
            oversized oxford shirt, flannel overshirt); knitwear (merino crewneck, knit polo \
            cardigan); outerwear (cropped denim jacket, tailored wool blazer, lightweight \
            trench coat, quilted puffer vest, technical rain jacket); bottoms (straight jeans, \
            pleated wide trousers, relaxed cargo pants); dresses & skirts (wrap jersey dress, \
            satin midi skirt); shoes (minimal white leather sneakers); accessories (wool \
            beanie, cashmere scarf). No ties, suits, belts or bags.""";

    private final com.example.shopupu.config.AiProperties aiProperties;
    private final LlmClient llmClient;
    private final SemanticSearchService semanticSearchService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public StylistChatResponse chat(StylistChatRequest request) {
        List<ChatMessage> conversation = toConversation(request);

        // LLM HTTP call first, no transaction open (ADR-0003)
        OutfitPlan plan = llmClient.planOutfit(conversation, CATALOG_CONTEXT).orElse(null);
        boolean degraded = plan == null || plan.slots() == null || plan.slots().isEmpty();
        if (degraded) {
            plan = StubLlmClient.keywordPlan(request.message());
        }
        meterRegistry.counter("shopupu.ai", "op", "stylist", "result", degraded ? "fallback" : "ok")
                .increment();

        List<String> unavailable = new ArrayList<>();
        if (plan.unavailable() != null) {
            unavailable.addAll(plan.unavailable());
        }

        List<StylistChatResponse.StylistSlot> slots = new ArrayList<>();
        java.util.Set<Long> alreadyRecommended = new java.util.HashSet<>();
        for (OutfitPlan.OutfitSlot slot : plan.slots().stream().limit(MAX_SLOTS).toList()) {
            SlotResolution resolution = resolveSlot(slot, alreadyRecommended);
            if (!resolution.products().isEmpty()) {
                resolution.products().forEach(item -> alreadyRecommended.add(item.id()));
                slots.add(new StylistChatResponse.StylistSlot(slot.slot(), resolution.products()));
            } else if (resolution.notInCatalog()
                    && unavailable.stream().noneMatch(existing -> existing.equalsIgnoreCase(slot.slot()))) {
                // honesty gate: nothing in the catalog is actually this garment
                unavailable.add(slot.slot());
            }
        }
        return new StylistChatResponse(plan.reply(), slots, List.copyOf(unavailable), degraded);
    }

    /** Semantic search per slot, post-filtered by the plan's constraints; a product never repeats across slots. */
    private SlotResolution resolveSlot(OutfitPlan.OutfitSlot slot, java.util.Set<Long> alreadyRecommended) {
        if (slot == null || slot.query() == null || slot.query().isBlank()) {
            return new SlotResolution(List.of(), false);
        }
        List<SemanticSearchService.ScoredItem> hits =
                semanticSearchService.semanticSearchScored(slot.query(), CANDIDATES_PER_SLOT);
        // relevance gate BEFORE dedupe: "taken by another slot" is not "not in catalog"
        List<SemanticSearchService.ScoredItem> relevant = hits.stream()
                .filter(hit -> hit.distance() == null
                        || hit.distance() <= aiProperties.getStylistMatchMaxDistance())
                .toList();
        if (relevant.isEmpty()) {
            // non-empty hits here means every hit carried a distance above the gate:
            // nothing in the catalog is actually this garment — say so, don't fake it
            return new SlotResolution(List.of(), !hits.isEmpty());
        }
        List<ProductListItem> candidates = relevant.stream()
                .map(SemanticSearchService.ScoredItem::item)
                .filter(item -> !alreadyRecommended.contains(item.id()))
                .toList();
        List<ProductListItem> filtered = candidates.stream()
                .filter(item -> genderMatches(slot.gender(), item.gender()))
                .filter(item -> slot.maxPrice() == null
                        || item.price() == null
                        || item.price().compareTo(slot.maxPrice()) <= 0)
                .limit(PRODUCTS_PER_SLOT)
                .toList();
        if (!filtered.isEmpty()) {
            return new SlotResolution(filtered, false);
        }
        // constraints filtered everything out: better a close match than an empty slot
        return new SlotResolution(candidates.stream().limit(PRODUCTS_PER_SLOT).toList(), false);
    }

    private record SlotResolution(List<ProductListItem> products, boolean notInCatalog) {
    }

    private boolean genderMatches(Gender wanted, Gender actual) {
        if (wanted == null || actual == null || wanted == Gender.UNISEX) {
            return true;
        }
        return actual == wanted || actual == Gender.UNISEX;
    }

    private List<ChatMessage> toConversation(StylistChatRequest request) {
        List<ChatMessage> conversation = new ArrayList<>();
        if (request.history() != null) {
            for (StylistChatRequest.HistoryMessage turn : request.history()) {
                conversation.add(new ChatMessage(turn.role(), turn.content()));
            }
        }
        conversation.add(new ChatMessage("user", request.message()));
        return conversation;
    }
}
