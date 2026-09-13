package com.example.shopupu.ai.gateway;

import com.example.shopupu.ai.guard.AiUnavailableException;
import com.example.shopupu.ai.guard.AiUsageGuard;
import com.example.shopupu.config.AiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Embeddings from a local Ollama daemon (production/dev default): the model
 * (e.g. bge-m3, 1024-dim, multilingual) runs on the same machine, so no vendor
 * key and no data leaves the perimeter. API: POST /api/embed
 * {"model": "...", "input": [...]} -> {"embeddings": [[...], ...]}.
 *
 * <p>bge-m3 needs no query/document instruction prefixes, so both sides embed
 * the raw text; cosine distance (pgvector {@code <=>}) is normalization-invariant.
 */
@Component
@ConditionalOnProperty(name = "ai.embedding-provider", havingValue = "ollama")
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final AiProperties aiProperties;
    private final RestClient restClient;
    private final AiUsageGuard usageGuard;

    public OllamaEmbeddingClient(AiProperties aiProperties, AiUsageGuard usageGuard) {
        this.aiProperties = aiProperties;
        this.usageGuard = usageGuard;
        Duration timeout = Duration.ofSeconds(aiProperties.getRequestTimeoutSeconds());
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        String baseUrl = aiProperties.getEmbeddingBaseUrl();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts);
    }

    @Override
    public int dimensions() {
        return aiProperties.getEmbeddingDim();
    }

    private List<float[]> embed(List<String> texts) {
        try (var permit = usageGuard.tryAcquire(texts, 0)) {
            if (permit == null) throw new AiUnavailableException();
            OllamaEmbedResponse response = restClient.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OllamaEmbedRequest(aiProperties.getEmbeddingModel(), texts))
                    .exchange((request, body) -> AiHttpResponse.read(
                            body, aiProperties.getMaxResponseBytes(), OllamaEmbedResponse.class));
            if (response == null) throw new AiUnavailableException();
            return AiHttpResponse.validateEmbeddings(response.embeddings(), texts.size(), dimensions());
        } catch (Exception exception) {
            throw new AiUnavailableException();
        }
    }

    private record OllamaEmbedRequest(String model, List<String> input) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaEmbedResponse(List<float[]> embeddings) {
    }
}
