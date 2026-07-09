package com.example.shopupu.ai.gateway;

import java.util.List;

/**
 * Pluggable text-embedding provider (same shape as payments/gateway): the
 * implementation is selected by {@code ai.embedding-provider}, and the stub
 * default keeps dev/tests fully offline.
 *
 * <p>Query and document embeddings are separate on purpose: retrieval models
 * such as voyage-3 embed the two sides asymmetrically ({@code input_type}).
 */
public interface EmbeddingClient {

    /** Embeds a search query (the "query" side of retrieval). */
    float[] embedQuery(String text);

    /** Embeds catalog documents (the "document" side of retrieval). */
    List<float[]> embedDocuments(List<String> texts);

    /** Vector dimension; must match the product_embeddings column (V15). */
    int dimensions();
}
