package com.example.shopupu.ai.service;

import com.example.shopupu.ai.event.ProductChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Re-indexes embeddings AFTER the catalog transaction commits and on the AI
 * pool: a broken embedding provider can never fail an admin catalog write
 * (same discipline as OrderNotificationListener).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEmbeddingListener {

    private final ProductEmbeddingService productEmbeddingService;

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductChanged(ProductChangedEvent event) {
        try {
            productEmbeddingService.indexProduct(event.productId());
        } catch (Exception ex) {
            // never propagate: indexing is a best-effort side effect
            log.warn("Failed to (re)index embedding for product {}", event.productId(), ex);
        }
    }
}
