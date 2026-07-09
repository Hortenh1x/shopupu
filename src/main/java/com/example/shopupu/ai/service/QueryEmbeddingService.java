package com.example.shopupu.ai.service;

import com.example.shopupu.ai.gateway.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Caches query vectors (Caffeine): repeated or popular searches must not
 * re-hit the embedding API on every request.
 */
@Service
@RequiredArgsConstructor
public class QueryEmbeddingService {

    private final EmbeddingClient embeddingClient;

    @Cacheable(cacheNames = "aiQueryEmbedding", key = "#query")
    public float[] embedQuery(String query) {
        return embeddingClient.embedQuery(query);
    }
}
