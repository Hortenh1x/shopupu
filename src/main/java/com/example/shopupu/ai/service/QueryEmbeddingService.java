package com.example.shopupu.ai.service;

import com.example.shopupu.ai.gateway.EmbeddingClient;
import com.example.shopupu.ai.guard.AiUnavailableException;
import com.example.shopupu.config.AiProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Caches query vectors (Caffeine): repeated or popular searches must not
 * re-hit the embedding API on every request.
 */
@Service
public class QueryEmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final AiProperties properties;
    private final Cache<String, float[]> cache;

    public QueryEmbeddingService(EmbeddingClient embeddingClient, AiProperties properties) {
        this.embeddingClient = embeddingClient;
        this.properties = properties;
        this.cache = Caffeine.newBuilder().maximumSize(properties.getQueryCacheEntries())
                .expireAfterWrite(Duration.ofMinutes(properties.getQueryCacheMinutes())).build();
    }

    public float[] embedQuery(String query) {
        if (!properties.isEnabled() || query == null || query.isBlank() || query.length() > 500) {
            throw new AiUnavailableException();
        }
        return cache.get(query, embeddingClient::embedQuery);
    }
}
