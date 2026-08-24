package com.example.shopupu.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * AI features (semantic search, recommendations, review summaries) — see
 * docs/ai-features-plan.md. With the default stub providers the application
 * makes no external AI calls and needs no API keys.
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** Master switch: off = no AI calls at all, every feature degrades gracefully. */
    private boolean enabled = false;

    @NotBlank
    @Pattern(regexp = "stub|ollama|local|voyage",
            message = "ai.embedding-provider must be one of: stub, ollama, local, voyage")
    private String embeddingProvider = "stub";

    @NotBlank
    @Pattern(regexp = "stub|deepseek",
            message = "ai.llm-provider must be one of: stub, deepseek")
    private String llmProvider = "stub";

    /** bge-m3 (Ollama/TEI) or voyage-3 — both 1024-dim, matching the V15 vector column. */
    @NotBlank
    private String embeddingModel = "bge-m3";

    /** Embedding endpoint: Ollama (http://localhost:11434) / TEI sidecar / Voyage base URL. */
    private String embeddingBaseUrl;

    /** Must equal the dimension of the product_embeddings.embedding column. */
    @NotNull
    @Min(64)
    @Max(4096)
    private Integer embeddingDim = 1024;

    /** All in-scope LLM tasks are single summarize/extract calls. deepseek-v4-flash is
     * DeepSeek's cheap fast V4 model; the client disables its thinking mode. (The old
     * deepseek-chat / deepseek-reasoner names are deprecated 2026-07-24.) */
    @NotBlank
    private String llmModel = "deepseek-v4-flash";

    /** OpenAI-compatible chat endpoint; also accepts any compatible server. */
    private String llmBaseUrl;

    private String llmApiKey;

    private String voyageApiKey;

    @NotNull
    @Min(1)
    @Max(60)
    private Integer requestTimeoutSeconds = 15;

    /** Products with fewer approved reviews than this get no summary. */
    @NotNull
    @Min(1)
    private Integer reviewSummaryMinReviews = 3;

    /**
     * Stylist honesty gate: a slot whose best cosine distance exceeds this is
     * treated as "not in the catalog" instead of recommending the nearest
     * unrelated garment. Empirically (bge-m3, this catalog): real matches score
     * ≤0.38, missing garment types ≥0.47.
     */
    @NotNull
    @jakarta.validation.constraints.DecimalMin("0.0")
    @jakarta.validation.constraints.DecimalMax("2.0")
    private Double stylistMatchMaxDistance = 0.45;
}
