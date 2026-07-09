package com.example.shopupu.ai.gateway;

import com.example.shopupu.config.AiProperties;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic offline embeddings (default, like the stub payment gateway):
 * the vector is seeded by the text's hash, so identical texts always map to
 * identical vectors — tests get reproducible KNN without any network.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.embedding-provider", havingValue = "stub", matchIfMissing = true)
public class StubEmbeddingClient implements EmbeddingClient {

    private final AiProperties aiProperties;

    @Override
    public float[] embedQuery(String text) {
        return vectorFor(text);
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return texts.stream().map(this::vectorFor).toList();
    }

    @Override
    public int dimensions() {
        return aiProperties.getEmbeddingDim();
    }

    private float[] vectorFor(String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase();
        Random random = new Random(normalized.hashCode());
        float[] vector = new float[dimensions()];
        double norm = 0;
        for (int i = 0; i < vector.length; i++) {
            vector[i] = random.nextFloat() * 2 - 1;
            norm += vector[i] * vector[i];
        }
        float scale = norm == 0 ? 1 : (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }
}
