package com.example.shopupu.catalog.mapper;

import com.example.shopupu.catalog.dto.BrandResponse;
import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductImageResponse;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.dto.VariantResponse;
import com.example.shopupu.catalog.entity.Brand;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductImage;
import com.example.shopupu.catalog.entity.ProductVariant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

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

    public BrandResponse toBrandResponse(Brand brand) {
        if (brand == null) {
            return null;
        }
        return new BrandResponse(brand.getId(), brand.getName(), brand.getSlug());
    }

    public VariantResponse toVariantResponse(ProductVariant variant, Integer available) {
        return new VariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getSize(),
                variant.getColor(),
                variant.getPrice(),
                variant.getOldPrice(),
                variant.getEnabled(),
                available
        );
    }

    /** availableByVariantId: variant id -> (stock - reserved), batch-loaded by the caller. */
    public ProductResponse toProductResponse(Product product, Map<Long, Integer> availableByVariantId) {
        if (product == null) {
            return null;
        }

        Category category = product.getCategory();
        Brand brand = product.getBrand();
        List<ProductResponse.ProductResponseImage> images = toProductImages(product);

        List<VariantResponse> variants = new ArrayList<>();
        if (product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                variants.add(toVariantResponse(variant,
                        availableByVariantId.getOrDefault(variant.getId(), 0)));
            }
            variants.sort(Comparator.comparing(VariantResponse::size,
                    Comparator.nullsLast(String::compareTo)));
        }

        return new ProductResponse(
                product.getId(),
                product.getTitle(),
                product.getSlug(),
                product.getDescription(),
                product.getPrice(),
                product.getOldPrice(),
                product.getEnabled(),
                product.getGender(),
                product.getSeason(),
                product.getMaterial(),
                product.getCareInstructions(),
                product.getMetaTitle(),
                product.getMetaDescription(),
                brand != null ? brand.getId() : null,
                brand != null ? brand.getName() : null,
                product.getCreatedAt(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                category != null ? category.getSlug() : null,
                images,
                variants
        );
    }

    public ProductListItem toProductListItem(Product product) {
        if (product == null) {
            return null;
        }

        Category category = product.getCategory();
        Brand brand = product.getBrand();
        ProductImage previewImage = firstProductImage(product);
        return new ProductListItem(
                product.getId(),
                product.getTitle(),
                product.getSlug(),
                product.getPrice(),
                product.getOldPrice(),
                brand != null ? brand.getName() : null,
                product.getGender(),
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
