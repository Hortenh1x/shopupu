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
}
