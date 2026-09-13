package com.example.shopupu.ai.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.ai.guard.AiUnavailableException;
import com.example.shopupu.ai.guard.AiUsageGuard;
import com.example.shopupu.config.AiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingProviderGuardTest {
    @Test
    void everyHttpEmbeddingProviderRejectsDisabledCallsBeforeNetwork() {
        AiProperties properties = new AiProperties();
        AiUsageGuard guard = new AiUsageGuard(properties);
        List<EmbeddingClient> clients = List.of(new OllamaEmbeddingClient(properties, guard),
                new LocalEmbeddingClient(properties, guard), new VoyageEmbeddingClient(properties, guard));
        for (EmbeddingClient client : clients) {
            assertThrows(AiUnavailableException.class, () -> client.embedQuery("synthetic query"));
            assertThrows(AiUnavailableException.class, () -> client.embedDocuments(List.of("synthetic product")));
        }
    }

    @Test
    void rejectsNonFiniteAndWrongDimensionVectors() {
        assertThrows(AiUnavailableException.class,
                () -> AiHttpResponse.validateEmbeddings(List.of(new float[] {Float.NaN}), 1, 1));
        assertThrows(AiUnavailableException.class,
                () -> AiHttpResponse.validateEmbeddings(List.of(new float[] {1}), 1, 2));
    }
}
