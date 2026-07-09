package com.example.shopupu.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.shopupu.config.AiProperties;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * describes the StubEmbeddingClientTest test class.
 */
class StubEmbeddingClientTest {

    private final StubEmbeddingClient client = new StubEmbeddingClient(properties());

    @Test
    void sameTextAlwaysProducesTheSameVector() {
        assertArrayEquals(client.embedQuery("red dress"), client.embedQuery("red dress"));
        // normalization must not change the seed
        assertArrayEquals(client.embedQuery("Red Dress "), client.embedQuery("red dress"));
    }

    @Test
    void differentTextsProduceDifferentVectors() {
        assertFalse(Arrays.equals(client.embedQuery("red dress"), client.embedQuery("blue jeans")));
    }

    @Test
    void vectorsHaveConfiguredDimensionAndUnitNorm() {
        float[] vector = client.embedQuery("hoodie");
        assertEquals(64, vector.length);
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        assertEquals(1.0, norm, 1e-3);
    }

    @Test
    void embedDocumentsEmbedsEachText() {
        List<float[]> vectors = client.embedDocuments(List.of("a", "b"));
        assertEquals(2, vectors.size());
        assertArrayEquals(client.embedQuery("a"), vectors.get(0));
    }

    private static AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.setEmbeddingDim(64);
        return properties;
    }
}
