package com.example.shopupu.ai.service;

import com.example.shopupu.ai.gateway.EmbeddingClient;
import com.example.shopupu.ai.repository.ProductEmbeddingRepository;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.config.AiProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes the semantic-search index. Same boundary discipline as payments
 * (ADR-0003): TX(read product snapshot) -> embedding HTTP call outside any
 * transaction -> single-row upsert. A DB connection is never held across the
 * network call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEmbeddingService {

    private static final int BACKFILL_PAGE_SIZE = 50;

    private final AiProperties aiProperties;
    private final EmbeddingClient embeddingClient;
    private final ProductEmbeddingRepository embeddingRepository;
    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public void indexProduct(Long productId) {
        if (!aiProperties.isEnabled()) {
            return;
        }
        EmbeddingSource source = transactionTemplate.execute(tx -> loadSource(productId));
        if (source == null || !source.sellable()) {
            // disabled / soft-deleted / gone: keep the KNN index clean
            embeddingRepository.deleteByProductId(productId);
            return;
        }
        float[] embedding = embedDocument(source.text());
        embeddingRepository.upsert(productId, aiProperties.getEmbeddingModel(), embedding);
    }

    @Async("aiExecutor")
    public void backfillMissingAsync() {
        int indexed = backfillMissing();
        log.info("Embedding backfill finished: {} products indexed", indexed);
    }

    /** Keyset loop so a persistently failing product cannot stall the backfill. */
    public int backfillMissing() {
        if (!aiProperties.isEnabled()) {
            return 0;
        }
        String model = aiProperties.getEmbeddingModel();
        int indexed = 0;
        Long afterId = 0L;
        while (true) {
            List<Long> ids = embeddingRepository.findProductIdsMissingEmbedding(model, afterId, BACKFILL_PAGE_SIZE);
            if (ids.isEmpty()) {
                return indexed;
            }
            for (Long id : ids) {
                try {
                    indexProduct(id);
                    indexed++;
                } catch (Exception ex) {
                    log.warn("Embedding backfill failed for product {}", id, ex);
                }
            }
            afterId = ids.get(ids.size() - 1);
        }
    }

    private float[] embedDocument(String text) {
        try {
            float[] embedding = embeddingClient.embedDocuments(List.of(text)).get(0);
            if (embedding.length != aiProperties.getEmbeddingDim()) {
                throw new IllegalStateException("Embedding dimension " + embedding.length
                        + " does not match ai.embedding-dim " + aiProperties.getEmbeddingDim());
            }
            meterRegistry.counter("shopupu.ai", "op", "embed_document", "result", "ok").increment();
            return embedding;
        } catch (RuntimeException ex) {
            meterRegistry.counter("shopupu.ai", "op", "embed_document", "result", "error").increment();
            throw ex;
        }
    }

    /** Snapshot is assembled inside the TX: brand/category/variants are lazy (OSIV off). */
    private EmbeddingSource loadSource(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return null;
        }
        boolean sellable = Boolean.TRUE.equals(product.getEnabled()) && !product.isDeleted();
        return new EmbeddingSource(sellable, buildEmbeddingText(product));
    }

    /** The searchable "document" for one product: text attributes + variant colors. */
    static String buildEmbeddingText(Product product) {
        StringJoiner joiner = new StringJoiner("\n");
        appendIfPresent(joiner, product.getTitle());
        appendIfPresent(joiner, product.getDescription());
        appendIfPresent(joiner, product.getBrand() != null ? product.getBrand().getName() : null);
        appendIfPresent(joiner, product.getCategory() != null ? product.getCategory().getName() : null);
        appendIfPresent(joiner, product.getGender() != null ? product.getGender().name() : null);
        appendIfPresent(joiner, product.getSeason());
        appendIfPresent(joiner, product.getMaterial());
        if (product.getVariants() != null) {
            Set<String> colors = new LinkedHashSet<>();
            for (ProductVariant variant : product.getVariants()) {
                if (variant.getColor() != null && !variant.getColor().isBlank()) {
                    colors.add(variant.getColor().trim());
                }
            }
            appendIfPresent(joiner, String.join(" ", colors));
        }
        return joiner.toString();
    }

    private static void appendIfPresent(StringJoiner joiner, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(value.trim());
        }
    }

    private record EmbeddingSource(boolean sellable, String text) {
    }
}
