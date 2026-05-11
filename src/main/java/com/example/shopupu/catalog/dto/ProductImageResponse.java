package com.example.shopupu.catalog.dto;

/**
 * describes the ProductImageResponse record.
 */
public record ProductImageResponse(
        Long id,
        String url,
        String altText,
        Integer sortOrder
) {
}
