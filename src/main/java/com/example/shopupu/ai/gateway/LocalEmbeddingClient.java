package com.example.shopupu.ai.gateway;

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

    public LocalEmbeddingClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        Duration timeout = Duration.ofSeconds(aiProperties.getRequestTimeoutSeconds());
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(required(aiProperties.getEmbeddingBaseUrl(), "ai.embedding-base-url"))
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
        float[][] response = restClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TeiEmbedRequest(texts, true))
                .retrieve()
                .body(float[][].class);
        if (response == null || response.length != texts.size()) {
            throw new IllegalStateException("Embedding sidecar returned an unexpected response");
        }
        return Arrays.asList(response);
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
