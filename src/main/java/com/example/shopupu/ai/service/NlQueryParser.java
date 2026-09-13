package com.example.shopupu.ai.service;

import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.model.ParsedProductQuery;
import com.example.shopupu.config.AiProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Caches LLM query parses (Caffeine): the same natural-language query is
 * parsed once, not per request.
 */
@Service
public class NlQueryParser {

    private final LlmClient llmClient;
    private final AiProperties properties;
    private final Cache<String, Optional<ParsedProductQuery>> cache;

    public NlQueryParser(LlmClient llmClient, AiProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.cache = Caffeine.newBuilder().maximumSize(properties.getQueryCacheEntries())
                .expireAfterWrite(Duration.ofMinutes(properties.getQueryCacheMinutes())).build();
    }

    public Optional<ParsedProductQuery> parse(String query) {
        if (!properties.isEnabled() || query == null || query.isBlank() || query.length() > 500) {
            return Optional.empty();
        }
        try {
            return cache.get(query, llmClient::parseCatalogQuery);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
