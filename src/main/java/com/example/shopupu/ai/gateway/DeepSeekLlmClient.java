package com.example.shopupu.ai.gateway;

import com.example.shopupu.ai.model.ParsedProductQuery;
import com.example.shopupu.ai.model.ReviewSummary;
import com.example.shopupu.config.AiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * DeepSeek for the LLM tasks (review summaries, NL query parsing) — the cheap,
 * fast {@code deepseek-v4-flash} with <b>thinking mode disabled</b>. V4 turns
 * thinking on by default; for bounded summarize/extract that only wastes tokens
 * and muddies the JSON, so every request sends {@code thinking:{type:"disabled"}}.
 *
 * <p>DeepSeek's API is OpenAI-compatible, so this is a plain RestClient against
 * {@code /chat/completions} in JSON mode — no SDK. {@code ai.llm-base-url} can
 * also point at any compatible server. Failures degrade to empty: an LLM outage
 * must never fail a shop request.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.llm-provider", havingValue = "deepseek")
public class DeepSeekLlmClient implements LlmClient {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    private static final String SUMMARY_SYSTEM = """
            You summarize customer reviews for an online clothing shop.
            Reply with ONE JSON object and nothing else:
            {"tldr": "2-3 sentence summary in the dominant language of the reviews",
             "pros": ["short positive point", ...up to 5],
             "cons": ["short negative point", ...up to 5, empty if none],
             "sentiment": "POSITIVE" | "MIXED" | "NEGATIVE"}
            Base every statement strictly on the supplied reviews; never invent details.""";

    private static final String PARSE_SYSTEM = """
            You convert a shopper's free-text clothing query into JSON filters.
            Reply with ONE JSON object and nothing else:
            {"q": "residual keywords (garment/style/fabric) in the query's own language, or null",
             "gender": "MEN" | "WOMEN" | "UNISEX" | "KIDS" | null,
             "size": "size like S, M, L, XL, 42 or null",
             "color": "color name in English or null",
             "minPrice": number or null,
             "maxPrice": number or null}
            Extract only what is explicitly stated; use null otherwise.
            'under 100' / 'до 100' means maxPrice 100.""";

    private final AiProperties aiProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DeepSeekLlmClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
        required(aiProperties.getLlmApiKey(), "ai.llm-api-key");
        Duration timeout = Duration.ofSeconds(aiProperties.getRequestTimeoutSeconds());
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        String baseUrl = aiProperties.getLlmBaseUrl();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Optional<ReviewSummary> summarizeReviews(String productTitle, List<String> reviewLines) {
        String user = "Product: " + productTitle + "\n\nReviews:\n" + String.join("\n", reviewLines);
        return complete(ReviewSummary.class, SUMMARY_SYSTEM, user);
    }

    @Override
    public Optional<ParsedProductQuery> parseCatalogQuery(String query) {
        return complete(ParsedProductQuery.class, PARSE_SYSTEM, query);
    }

    private <T> Optional<T> complete(Class<T> type, String system, String user) {
        try {
            ChatRequest request = new ChatRequest(
                    aiProperties.getLlmModel(),
                    List.of(new Message("system", system), new Message("user", user)),
                    new ResponseFormat("json_object"),
                    new Thinking("disabled"),
                    0.0,
                    false);
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + aiProperties.getLlmApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return Optional.empty();
            }
            String content = response.choices().get(0).message().content();
            if (content == null || content.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(content, type));
        } catch (Exception ex) {
            log.warn("DeepSeek call failed for {}: {}", type.getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    private void required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be configured");
        }
    }

    private record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("response_format") ResponseFormat responseFormat,
            Thinking thinking,
            double temperature,
            boolean stream
    ) {
    }

    private record Message(String role, String content) {
    }

    private record ResponseFormat(String type) {
    }

    // deepseek-v4-flash defaults to thinking ON; force it off for summarize/extract
    private record Thinking(String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(Message message) {
    }
}
