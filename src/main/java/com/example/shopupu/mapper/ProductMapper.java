//package com.example.shopupu.mapper;
//
//import com.example.shopupu.catalog.dto.ProductRequest;
//import com.example.shopupu.catalog.dto.ProductResponse;
//import com.example.shopupu.catalog.entity.Product;
//import org.springframework.stereotype.Component;
//
//@Component
//public class ProductMapper {
//    public Product toEntity(ProductRequest dto) {
//        return Product.builder()
//                .title(dto.title())
//                .description(dto.description())
//                .price(dto.price())
//                .sku(dto.sku())
//                .stock(dto.stock())
//                .build();
//    }
//
//    public ProductResponse toResponse(Product entity) {
//        return new ProductResponse(
//                entity.getId(),
//                entity.getTitle(),
//                entity.getDescription(),
//                entity.getPrice(),
//                entity.getSku(),
//                entity.getStock()
//        );
//    }
//}
