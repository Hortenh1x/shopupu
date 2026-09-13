package com.example.shopupu.ai.gateway;

import com.example.shopupu.ai.guard.AiUnavailableException;
import com.example.shopupu.ai.guard.AiUsageGuard;
import com.example.shopupu.config.AiProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Self-hosted embedding sidecar (production default): a small multilingual
 * model (e.g. BAAI/bge-m3, 1024-dim) served by HuggingFace
 * text-embeddings-inference; API: POST /embed {"inputs": [...]} -> [[...]].
 * Data never leaves the perimeter and no vendor key is needed.
 */
@Component
@ConditionalOnProperty(name = "ai.embedding-provider", havingValue = "local")
public class LocalEmbeddingClient implements EmbeddingClient {

    private final AiProperties aiProperties;
    private final RestClient restClient;
    private final AiUsageGuard usageGuard;

    public LocalEmbeddingClient(AiProperties aiProperties, AiUsageGuard usageGuard) {
        this.aiProperties = aiProperties;
        this.usageGuard = usageGuard;
        Duration timeout = Duration.ofSeconds(aiProperties.getRequestTimeoutSeconds());
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(aiProperties.isEnabled()
                        ? required(aiProperties.getEmbeddingBaseUrl(), "ai.embedding-base-url")
                        : "http://localhost:8081")
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
            float[][] response = restClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new TeiEmbedRequest(texts, true))
                    .exchange((request, body) -> AiHttpResponse.read(
                            body, aiProperties.getMaxResponseBytes(), float[][].class));
            if (response == null) throw new AiUnavailableException();
            return AiHttpResponse.validateEmbeddings(Arrays.asList(response), texts.size(), dimensions());
        } catch (Exception exception) {
            throw new AiUnavailableException();
        }
    }

    private String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured");
        }
        return value;
    }

    private record TeiEmbedRequest(List<String> inputs, boolean normalize) {
    }
}
