package com.example.shopupu.reviews.mapper;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.reviews.dto.AdminReviewResponse;
import com.example.shopupu.reviews.dto.ReviewResponse;
import com.example.shopupu.reviews.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReviewMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "username", source = "user", qualifiedByName = "publicDisplayName")
    ReviewResponse toResponse(Review review);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productTitle", source = "product.title")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userEmail", source = "user.email")
    AdminReviewResponse toAdminResponse(Review review);

    // never expose the email publicly (PII)
    @Named("publicDisplayName")
    default String publicDisplayName(User user) {
        return user != null && user.getUsername() != null ? user.getUsername() : "Customer";
    }
}
