package com.example.shopupu.catalog.dto;


/**
 * describes the CategoryResponse record.
 */
public record CategoryResponse(
        Long id,
        String name,
        String slug,
        String description,
        Long parentId
) {
}
