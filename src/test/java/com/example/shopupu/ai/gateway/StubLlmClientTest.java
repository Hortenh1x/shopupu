package com.example.shopupu.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * describes the StubLlmClientTest test class.
 */
class StubLlmClientTest {

    private final StubLlmClient client = new StubLlmClient();

    @Test
    void summarizeReturnsCannedSummaryBasedOnFirstReview() {
        var summary = client.summarizeReviews("Hoodie", List.of("[5/5] Great — warm and soft", "[4/5] ok"));

        assertTrue(summary.isPresent());
        assertTrue(summary.get().tldr().contains("Hoodie"));
        assertTrue(summary.get().tldr().contains("2 reviews"));
    }

    @Test
    void summarizeIsEmptyWithoutReviews() {
        assertTrue(client.summarizeReviews("Hoodie", List.of()).isEmpty());
    }

    @Test
    void parsePassesQueryThroughWithoutExtraction() {
        var parsed = client.parseCatalogQuery("тёплая куртка до 100");

        assertTrue(parsed.isPresent());
        assertEquals("тёплая куртка до 100", parsed.get().q());
        assertEquals(null, parsed.get().maxPrice());
    }

    @Test
    void keywordPlanHonestlyNamesGarmentsTheShopDoesNotCarry() {
        var plan = StubLlmClient.keywordPlan("нужен костюм с галстуком на деловую встречу");

        // the shopper's own words are echoed back
        assertEquals(List.of("костюм", "галстуком"), plan.unavailable());
        assertTrue(plan.reply().startsWith("We don't carry костюм, галстуком"));
        // still offers the closest formal pieces instead of nothing
        assertEquals("Blazer", plan.slots().get(0).slot());
    }

    @Test
    void keywordPlanDoesNotFalseFlagShortEnglishStemsInsideWords() {
        // "tie" inside "sweatier", "cap" inside "escape" must not trigger honesty notes
        var plan = StubLlmClient.keywordPlan("something sweatier to escape the cold");

        assertTrue(plan.unavailable().isEmpty());
    }

    @Test
    void keywordPlanDoesNotReadGenderOutOfUnrelatedWords() {
        // "нужен" contains "жен": must NOT be taken as a womenswear request,
        // otherwise the gender filter silently drops the men's blazer
        var plan = StubLlmClient.keywordPlan("нужен пиджак на деловую встречу");

        assertEquals("Blazer", plan.slots().get(0).slot());
        assertEquals(null, plan.slots().get(0).gender());
    }

    @Test
    void keywordPlanStillDetectsRealGenderWords() {
        var plan = StubLlmClient.keywordPlan("что-то официальное для женщины");

        assertEquals(com.example.shopupu.catalog.entity.Gender.WOMEN, plan.slots().get(0).gender());
    }
}
