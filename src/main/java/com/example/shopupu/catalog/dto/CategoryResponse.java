package com.example.shopupu.catalog.dto;

/**
 * DTO returned to clients for category representation.
 */
public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        Long parentId
) {
}

