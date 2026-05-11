package com.example.shopupu.catalog.mapper;

import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductImageResponse;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductImage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class CatalogMapper {

    public CategoryResponse toCategoryResponse(Category category) {
        if (category == null) {
            return null;
        }

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
        if (product == null) {
            return null;
        }

        Category category = product.getCategory();
        List<ProductResponse.ProductResponseImage> images = toProductImages(product);

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
        if (product == null) {
            return null;
        }

        Category category = product.getCategory();
        ProductImage previewImage = firstProductImage(product);
        return new ProductListItem(
                product.getId(),
                product.getTitle(),
                product.getPrice(),
                product.getEnabled(),
                product.getCreatedAt(),
                category != null ? category.getId() : null,
                category != null ? category.getSlug() : null,
                previewImage != null ? previewImage.getUrl() : null,
                previewImage != null ? previewImage.getAltText() : null
        );
    }

    public ProductImageResponse toProductImageResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getId(),
                image.getUrl(),
                image.getAltText(),
                image.getPosition()
        );
    }

    private List<ProductResponse.ProductResponseImage> toProductImages(Product product) {
        if (product.getImages() == null) {
            return List.of();
        }

        List<ProductImage> sortedImages = new ArrayList<>();
        for (ProductImage image : product.getImages()) {
            if (image != null) {
                sortedImages.add(image);
            }
        }
        sortedImages.sort(Comparator.comparing(ProductImage::getPosition, Comparator.nullsLast(Integer::compareTo)));

        List<ProductResponse.ProductResponseImage> responses = new ArrayList<>();
        for (ProductImage image : sortedImages) {
            responses.add(new ProductResponse.ProductResponseImage(
                    image.getId(),
                    image.getUrl(),
                    image.getAltText(),
                    image.getPosition()
            ));
        }
        return responses;
    }

    private ProductImage firstProductImage(Product product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }

        ProductImage firstImage = null;
        for (ProductImage image : product.getImages()) {
            if (image == null) {
                continue;
            }
            if (firstImage == null) {
                firstImage = image;
                continue;
            }

            Integer currentPosition = image.getPosition();
            Integer firstPosition = firstImage.getPosition();
            if (firstPosition == null || (currentPosition != null && currentPosition < firstPosition)) {
                firstImage = image;
            }
        }
        return firstImage;
    }
}
