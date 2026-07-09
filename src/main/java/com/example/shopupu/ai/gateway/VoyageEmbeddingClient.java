package com.example.shopupu.ai.gateway;

import com.example.shopupu.config.AiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Voyage AI embeddings (Anthropic's embeddings partner; Claude itself has no
 * embeddings endpoint). Used when a larger managed model is wanted instead of
 * the local sidecar; voyage-3 is 1024-dim like the local bge-m3, so switching
 * providers needs no re-migration — only a re-embed backfill.
 */
@Component
@ConditionalOnProperty(name = "ai.embedding-provider", havingValue = "voyage")
public class VoyageEmbeddingClient implements EmbeddingClient {

    private static final String DEFAULT_BASE_URL = "https://api.voyageai.com";

    private final AiProperties aiProperties;
    private final RestClient restClient;

    public VoyageEmbeddingClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        required(aiProperties.getVoyageApiKey(), "ai.voyage-api-key");
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
        return embed(List.of(text), "query").get(0);
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, "document");
    }

    @Override
    public int dimensions() {
        return aiProperties.getEmbeddingDim();
    }

    private List<float[]> embed(List<String> texts, String inputType) {
        VoyageEmbeddingsResponse response = restClient.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + aiProperties.getVoyageApiKey())
                .body(new VoyageEmbeddingsRequest(texts, aiProperties.getEmbeddingModel(), inputType))
                .retrieve()
                .body(VoyageEmbeddingsResponse.class);
        if (response == null || response.data() == null || response.data().size() != texts.size()) {
            throw new IllegalStateException("Voyage returned an unexpected embeddings response");
        }
        return response.data().stream()
                .sorted(Comparator.comparingInt(VoyageEmbeddingData::index))
                .map(VoyageEmbeddingData::embedding)
                .toList();
    }

    private void required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured");
        }
    }

    private record VoyageEmbeddingsRequest(
            List<String> input,
            String model,
            @JsonProperty("input_type") String inputType
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VoyageEmbeddingsResponse(List<VoyageEmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VoyageEmbeddingData(float[] embedding, int index) {
    }
}
