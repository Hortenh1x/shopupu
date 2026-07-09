package com.example.shopupu.ai.gateway;

import com.example.shopupu.ai.model.ParsedProductQuery;
import com.example.shopupu.ai.model.ReviewSummary;
import java.util.List;
import java.util.Optional;

/**
 * Pluggable LLM provider with task-level methods (mirrors NotificationService
 * rather than exposing a generic prompt API): callers stay vendor-agnostic and
 * the stub can produce sensible canned output per task.
 *
 * <p>Empty result = "unavailable" (provider error, refusal, no key): callers
 * must degrade gracefully, never fail the request.
 */
public interface LlmClient {

    Optional<ReviewSummary> summarizeReviews(String productTitle, List<String> reviewLines);

    Optional<ParsedProductQuery> parseCatalogQuery(String query);
}
