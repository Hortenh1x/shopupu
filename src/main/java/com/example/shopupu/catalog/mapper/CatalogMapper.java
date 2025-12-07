package com.example.shopupu.catalog.mapper;

import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductImage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Maps catalog entities to DTOs.
 */
@Component
public class CatalogMapper {

    public CategoryResponse toCategoryResponse(Category category) {
        if (category == null) return null;
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                parentId
        );
    }

    public ProductResponse toProductResponse(Product product) {
        if (product == null) return null;
        Category category = product.getCategory();
        List<ProductResponse.ProductResponseImage> images = product.getImages() == null
                ? List.of()
                : product.getImages().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ProductImage::getPosition, Comparator.nullsLast(Integer::compareTo)))
                .map(image -> new ProductResponse.ProductResponseImage(
                        image.getId(),
                        image.getUrl(),
                        image.getAltText(),
                        image.getPosition()
                ))
                .collect(Collectors.toList());

        return new ProductResponse(
                product.getId(),
                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getSku(),
                product.getStock(),
                product.getEnabled(),
                product.getCreatedAt(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                category != null ? category.getSlug() : null,
                images
        );
    }

    public ProductListItem toProductListItem(Product product) {
        if (product == null) return null;
        Category category = product.getCategory();
        return new ProductListItem(
                product.getId(),
                product.getTitle(),
                product.getPrice(),
                product.getEnabled(),
                product.getCreatedAt(),
                category != null ? category.getId() : null,
                category != null ? category.getSlug() : null
        );
    }
}

