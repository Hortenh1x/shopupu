package com.example.shopupu.ai.service;

import com.example.shopupu.ai.dto.StylistChatRequest;
import com.example.shopupu.ai.dto.StylistChatResponse;
import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.gateway.StubLlmClient;
import com.example.shopupu.ai.model.ChatMessage;
import com.example.shopupu.ai.model.OutfitPlan;
import com.example.shopupu.ai.model.TextScript;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.entity.Gender;
import java.math.BigDecimal;
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
        StylistConstraints constraints = StylistConstraints.from(request);

        // LLM HTTP call first, no transaction open (ADR-0003)
        OutfitPlan plan = null;
        if (aiProperties.isEnabled() && !"stub".equals(aiProperties.getLlmProvider())) {
            try {
                plan = llmClient.planOutfit(conversation, CATALOG_CONTEXT).orElse(null);
            } catch (RuntimeException exception) {
                log.warn("Stylist generation unavailable; using offline selection");
            }
        }
        boolean degraded = !validPlan(plan);
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
        TextScript replyScript = TextScript.of(plan.reply());
        BigDecimal remainingBudget = constraints.maxTotalPrice();
        for (OutfitPlan.OutfitSlot slot : plan.slots().stream().limit(MAX_SLOTS).toList()) {
            SlotResolution resolution = resolveSlot(slot, alreadyRecommended, constraints.gender(), remainingBudget, degraded);
            if (!resolution.products().isEmpty()) {
                if (remainingBudget != null) {
                    remainingBudget = remainingBudget.subtract(resolution.products().stream()
                            .map(ProductListItem::price).reduce(BigDecimal.ZERO, BigDecimal::add));
                }
                resolution.products().forEach(item -> alreadyRecommended.add(item.id()));
                slots.add(new StylistChatResponse.StylistSlot(
                        slotLabel(slot, resolution.products(), replyScript), resolution.products()));
            } else if (resolution.notInCatalog()
                    && unavailable.stream().noneMatch(existing -> existing.equalsIgnoreCase(slot.slot()))) {
                // honesty gate: nothing in the catalog is actually this garment
                unavailable.add(slot.slot());
            }
        }
        String reply = slots.isEmpty()
                ? "No matching products are available for these constraints. Try changing the budget or requested pieces."
                : unavailable.isEmpty() ? plan.reply()
                : "These available pieces fit your constraints. Some requested pieces have no eligible match.";
        if (degraded) reply = "AI assistance is unavailable. Using an offline catalog selection. " + reply;
        return new StylistChatResponse(reply, slots, List.copyOf(unavailable), degraded);
    }

    private boolean validPlan(OutfitPlan plan) {
        if (plan == null || plan.reply() == null || plan.reply().isBlank() || plan.reply().length() > 1000
                || plan.slots() == null || plan.slots().isEmpty() || plan.slots().size() > MAX_SLOTS) return false;
        if (plan.unavailable() != null && (plan.unavailable().size() > 10
                || plan.unavailable().stream().anyMatch(value -> value == null || value.length() > 80))) return false;
        return plan.slots().stream().allMatch(slot -> slot != null
                && slot.slot() != null && !slot.slot().isBlank() && slot.slot().length() <= 80
                && slot.query() != null && !slot.query().isBlank() && slot.query().length() <= 300
                && (slot.maxPrice() == null || slot.maxPrice().signum() >= 0));
    }

    /** Semantic search per slot, post-filtered by the plan's constraints; a product never repeats across slots. */
    private SlotResolution resolveSlot(OutfitPlan.OutfitSlot slot, java.util.Set<Long> alreadyRecommended,
            Gender requestedGender, BigDecimal remainingBudget, boolean offline) {
        if (slot == null || slot.query() == null || slot.query().isBlank()) {
            return new SlotResolution(List.of(), false);
        }
        List<SemanticSearchService.ScoredItem> hits =
                offline ? semanticSearchService.keywordSearchScored(slot.query(), CANDIDATES_PER_SLOT)
                        : semanticSearchService.semanticSearchScored(slot.query(), CANDIDATES_PER_SLOT);
        // relevance gate BEFORE dedupe: "taken by another slot" is not "not in catalog"
        List<SemanticSearchService.ScoredItem> relevant = hits.stream()
                .filter(hit -> hit.distance() == null
                        || hit.distance() <= aiProperties.getStylistMatchMaxDistance())
                .toList();
        if (relevant.isEmpty()) {
            // non-empty hits here means every hit carried a distance above the gate:
            // nothing in the catalog is actually this garment — say so, don't fake it
            return new SlotResolution(List.of(), true);
        }
        List<ProductListItem> candidates = relevant.stream()
                .map(SemanticSearchService.ScoredItem::item)
                .filter(item -> item != null && item.id() != null && Boolean.TRUE.equals(item.enabled()))
                .filter(item -> !alreadyRecommended.contains(item.id()))
                .toList();
        List<ProductListItem> filtered = candidates.stream()
                .filter(item -> genderMatches(requestedGender == null ? slot.gender() : requestedGender, item.gender()))
                .filter(item -> item.price() != null && item.price().signum() >= 0)
                .filter(item -> remainingBudget == null || item.price().compareTo(remainingBudget) <= 0)
                .filter(item -> slot.maxPrice() == null
                        || item.price().compareTo(slot.maxPrice()) <= 0)
                .limit(PRODUCTS_PER_SLOT)
                .toList();
        if (!filtered.isEmpty()) {
            return new SlotResolution(filtered, false);
        }
        // A hard constraint can remove the last candidate. Never put it back.
        return new SlotResolution(List.of(), !candidates.isEmpty());
    }

    private record SlotResolution(List<ProductListItem> products, boolean notInCatalog) {
    }

    /**
     * A slot label is a heading printed above real products, so it must read in the
     * same script as the reply. The prompt asks for one language per plan, but the
     * model occasionally answers in English and labels the slots in Cyrillic, which
     * would render a mixed-language panel. When the two disagree, the catalog's own
     * product title is the safe label — it is real, and it matches the shop's language.
     * The {@code unavailable} list is left alone on purpose: it quotes the shopper.
     */
    private String slotLabel(OutfitPlan.OutfitSlot slot, List<ProductListItem> products, TextScript replyScript) {
        TextScript labelScript = TextScript.of(slot.slot());
        if (labelScript == TextScript.UNDETERMINED
                || replyScript == TextScript.UNDETERMINED
                || labelScript == replyScript) {
            return slot.slot();
        }
        return products.isEmpty() || products.get(0).title() == null ? slot.slot() : products.get(0).title();
    }

    private boolean genderMatches(Gender wanted, Gender actual) {
        if (wanted == null) return true;
        return actual == wanted || ((wanted == Gender.MEN || wanted == Gender.WOMEN) && actual == Gender.UNISEX);
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
