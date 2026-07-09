package com.example.shopupu.ai.service;

import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.model.ParsedProductQuery;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Caches LLM query parses (Caffeine): the same natural-language query is
 * parsed once, not per request.
 */
@Service
@RequiredArgsConstructor
public class NlQueryParser {

    private final LlmClient llmClient;

    @Cacheable(cacheNames = "aiNlQuery", key = "#query")
    public Optional<ParsedProductQuery> parse(String query) {
        return llmClient.parseCatalogQuery(query);
    }
}
