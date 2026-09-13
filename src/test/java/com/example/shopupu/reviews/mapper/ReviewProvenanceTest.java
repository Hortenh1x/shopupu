package com.example.shopupu.reviews.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewSource;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ReviewProvenanceTest {
    private final ReviewMapper mapper = Mappers.getMapper(ReviewMapper.class);

    @Test
    void syntheticProvenanceSurvivesPublicAndAdminMapping() {
        Review review = new Review();
        Product product = new Product();
        product.setId(4L);
        review.setProduct(product);
        review.setUser(User.builder().id(2L).email("fake@example.invalid").username("Demo author").build());
        review.setSource(ReviewSource.SYNTHETIC_DEMO);
        assertEquals(ReviewSource.SYNTHETIC_DEMO, mapper.toResponse(review).source());
        assertEquals(ReviewSource.SYNTHETIC_DEMO, mapper.toAdminResponse(review).source());
        assertFalse(mapper.toResponse(review).username().contains("@"));
    }

    @Test
    void submittedReviewDoesNotClaimSyntheticOrVerifiedRealPurchase() {
        assertEquals(ReviewSource.CUSTOMER_SUBMITTED, new Review().getSource());
    }
}
