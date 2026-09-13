package com.example.shopupu.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.shopupu.ai.gateway.EmbeddingClient;
import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.guard.AiUnavailableException;
import com.example.shopupu.ai.model.ParsedProductQuery;
import com.example.shopupu.config.AiProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiQueryCacheTest {
    @Test
    void repeatedQueriesUseTheCacheAndDisabledBypassesEvenCachedResults() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        EmbeddingClient embeddings = mock(EmbeddingClient.class);
        LlmClient llm = mock(LlmClient.class);
        when(embeddings.embedQuery("shirt")).thenReturn(new float[] {1});
        when(llm.parseCatalogQuery("shirt")).thenReturn(Optional.of(
                new ParsedProductQuery("shirt", null, null, null, null, null)));
        var vectors = new QueryEmbeddingService(embeddings, properties);
        var parser = new NlQueryParser(llm, properties);
        assertArrayEquals(vectors.embedQuery("shirt"), vectors.embedQuery("shirt"));
        assertEquals(parser.parse("shirt"), parser.parse("shirt"));
        verify(embeddings, times(1)).embedQuery("shirt");
        verify(llm, times(1)).parseCatalogQuery("shirt");
        properties.setEnabled(false);
        assertThrows(AiUnavailableException.class, () -> vectors.embedQuery("shirt"));
        assertTrue(parser.parse("shirt").isEmpty());
        verifyNoMoreInteractions(embeddings, llm);
    }
}
