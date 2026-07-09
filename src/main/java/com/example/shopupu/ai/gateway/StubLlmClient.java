package com.example.shopupu.ai.gateway;

import com.example.shopupu.ai.model.ParsedProductQuery;
import com.example.shopupu.ai.model.ReviewSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic canned output (default, mirrors LoggingNotificationService):
 * dev and tests see the features working end-to-end without any API key.
 */
@Component
@ConditionalOnProperty(name = "ai.llm-provider", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    private static final int TLDR_SNIPPET_LENGTH = 160;

    @Override
    public Optional<ReviewSummary> summarizeReviews(String productTitle, List<String> reviewLines) {
        if (reviewLines == null || reviewLines.isEmpty()) {
            return Optional.empty();
        }
        String first = reviewLines.get(0);
        String tldr = "Customers left " + reviewLines.size() + " reviews of " + productTitle + ". "
                + first.substring(0, Math.min(first.length(), TLDR_SNIPPET_LENGTH));
        return Optional.of(new ReviewSummary(
                tldr, List.of("mentioned by buyers"), List.of(), ReviewSummary.Sentiment.MIXED));
    }

    @Override
    public Optional<ParsedProductQuery> parseCatalogQuery(String query) {
        // no extraction: the whole query flows into keyword search unchanged
        return Optional.of(new ParsedProductQuery(query, null, null, null, null, null));
    }
}
