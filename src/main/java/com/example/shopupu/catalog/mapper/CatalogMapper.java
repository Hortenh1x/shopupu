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
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CatalogMapper {

    @Mapping(target = "parentId", source = "parent.id")
    CategoryResponse toCategoryResponse(Category category);

    BrandResponse toBrandResponse(Brand brand);

    @Mapping(target = "available", source = "available")
    VariantResponse toVariantResponse(ProductVariant variant, Integer available);

    /** availableByVariantId: variant id -> (stock - reserved), batch-loaded by the caller. */
    @Mapping(target = "brandId", source = "brand.id")
    @Mapping(target = "brandName", source = "brand.name")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "categorySlug", source = "category.slug")
    @Mapping(target = "images", source = "product", qualifiedByName = "positionSortedImages")
    @Mapping(target = "variants", source = "product", qualifiedByName = "sizeSortedVariants")
    ProductResponse toProductResponse(Product product, @Context Map<Long, Integer> availableByVariantId);

    @Mapping(target = "brandName", source = "brand.name")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categorySlug", source = "category.slug")
    @Mapping(target = "imageUrl", source = "product", qualifiedByName = "previewImageUrl")
    @Mapping(target = "imageAltText", source = "product", qualifiedByName = "previewImageAltText")
    ProductListItem toProductListItem(Product product);

    @Mapping(target = "sortOrder", source = "position")
    ProductImageResponse toProductImageResponse(ProductImage image);

    ProductResponse.ProductResponseImage toProductResponseImage(ProductImage image);

    @Named("positionSortedImages")
    default List<ProductResponse.ProductResponseImage> positionSortedImages(Product product) {
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
            responses.add(toProductResponseImage(image));
        }
        return responses;
    }

    @Named("sizeSortedVariants")
    default List<VariantResponse> sizeSortedVariants(Product product, @Context Map<Long, Integer> availableByVariantId) {
        List<VariantResponse> variants = new ArrayList<>();
        if (product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                variants.add(toVariantResponse(variant,
                        availableByVariantId.getOrDefault(variant.getId(), 0)));
            }
            variants.sort(Comparator.comparing(VariantResponse::size,
                    Comparator.nullsLast(String::compareTo)));
        }
        return variants;
    }

    @Named("previewImageUrl")
    default String previewImageUrl(Product product) {
        ProductImage previewImage = firstProductImage(product);
        return previewImage != null ? previewImage.getUrl() : null;
    }

    @Named("previewImageAltText")
    default String previewImageAltText(Product product) {
        ProductImage previewImage = firstProductImage(product);
        return previewImage != null ? previewImage.getAltText() : null;
    }

    default ProductImage firstProductImage(Product product) {
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
