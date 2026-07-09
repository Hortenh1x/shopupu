package com.example.shopupu.ai.event;

/**
 * Published by CatalogService after any product/variant mutation; consumed
 * AFTER_COMMIT to (re)index the product embedding (event lives with its
 * consumer, like OrderStatusChangedEvent in notifications).
 */
public record ProductChangedEvent(Long productId) {
}
