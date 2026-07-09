package com.example.shopupu.ai.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shopupu.ai.gateway.EmbeddingClient;
import com.example.shopupu.ai.model.ReviewSummary;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.config.AiProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V15-V17 against real pgvector PostgreSQL: KNN ordering, sellability filters,
 * co-occurrence recomputation and the jsonb summary round-trip.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AiRepositoriesIT extends PostgresContainerSupport {

    @Autowired
    private ProductEmbeddingRepository embeddingRepository;

    @Autowired
    private ProductRecommendationRepository recommendationRepository;

    @Autowired
    private ReviewSummaryRepository summaryRepository;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private Product redDress;
    private Product blueJeans;
    private Product hiddenGown;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("delete from product_embeddings").update();
        jdbcClient.sql("delete from product_recommendations").update();
        jdbcClient.sql("delete from product_review_summary").update();

        Category category = categoryRepository.save(
                new Category("Clothing", "clothing-" + System.nanoTime(), null, null));
        redDress = saveProduct("Red dress", category, true);
        blueJeans = saveProduct("Blue jeans", category, true);
        hiddenGown = saveProduct("Red gown", category, false);

        index(redDress, "red dress");
        index(blueJeans, "blue jeans");
        index(hiddenGown, "red gown");
    }

    @Test
    void knnReturnsNearestSellableProductsFirst() {
        // the stub embedder maps identical text to identical vectors
        float[] query = embeddingClient.embedQuery("red dress");

        List<Long> ids = embeddingRepository.findNearestProductIds(
                query, aiProperties.getEmbeddingModel(), 10);

        assertEquals(redDress.getId(), ids.get(0));
        assertFalse(ids.contains(hiddenGown.getId()), "disabled products must never surface");
    }

    @Test
    void similarExcludesTheAnchorProduct() {
        List<Long> ids = embeddingRepository.findSimilarProductIds(
                redDress.getId(), aiProperties.getEmbeddingModel(), 10);

        assertFalse(ids.contains(redDress.getId()));
        assertTrue(ids.contains(blueJeans.getId()));
        assertFalse(ids.contains(hiddenGown.getId()));
    }

    @Test
    void missingEmbeddingsKeysetFindsUnindexedProducts() {
        embeddingRepository.deleteByProductId(blueJeans.getId());

        List<Long> missing = embeddingRepository.findProductIdsMissingEmbedding(
                aiProperties.getEmbeddingModel(), 0L, 100);

        assertTrue(missing.contains(blueJeans.getId()));
        assertFalse(missing.contains(redDress.getId()));
        assertFalse(missing.contains(hiddenGown.getId()), "unsellable products are not backfilled");
    }

    @Test
    void boughtTogetherIsComputedFromPaidOrderCoOccurrence() {
        seedPaidOrderWith(redDress.getId(), blueJeans.getId());
        seedPaidOrderWith(redDress.getId(), blueJeans.getId());

        int pairs = recommendationRepository.recomputeBoughtTogether(10, 2);

        assertEquals(2, pairs);
        assertEquals(List.of(blueJeans.getId()), recommendationRepository.findRecommendedProductIds(
                redDress.getId(), ProductRecommendationRepository.KIND_BOUGHT_TOGETHER, 5));
        assertEquals(List.of(blueJeans.getId()), recommendationRepository.findPopularInSameCategory(
                redDress.getId(), 5));
    }

    @Test
    void reviewSummaryJsonbRoundTrips() {
        summaryRepository.upsert(redDress.getId(),
                new ReviewSummary("Loved overall", List.of("warm", "true to size"), List.of("pricey"),
                        ReviewSummary.Sentiment.POSITIVE),
                4, "claude-haiku-4-5");

        var summary = summaryRepository.findByProductId(redDress.getId()).orElseThrow();

        assertEquals("Loved overall", summary.tldr());
        assertEquals(List.of("warm", "true to size"), summary.pros());
        assertEquals(List.of("pricey"), summary.cons());
        assertEquals("POSITIVE", summary.sentiment());
        assertEquals(4, summary.basedOnReviews());

        summaryRepository.deleteByProductId(redDress.getId());
        assertTrue(summaryRepository.findByProductId(redDress.getId()).isEmpty());
    }

    private void index(Product product, String text) {
        embeddingRepository.upsert(product.getId(), aiProperties.getEmbeddingModel(),
                embeddingClient.embedDocuments(List.of(text)).get(0));
    }

    private Product saveProduct(String title, Category category, boolean enabled) {
        Product product = new Product(title, title.toLowerCase().replace(' ', '-') + "-" + System.nanoTime(),
                title, new BigDecimal("10.00"), category);
        product.setEnabled(enabled);
        return productRepository.save(product);
    }

    private void seedPaidOrderWith(Long productIdA, Long productIdB) {
        User user = userRepository.save(User.builder()
                .email("ai-it-" + System.nanoTime() + "@example.com")
                .passwordHash("test-hash")
                .enabled(true)
                .build());
        Long orderId = jdbcClient.sql("""
                        insert into orders (user_id, order_number, status)
                        values (:userId, :orderNumber, 'PAID')
                        returning id
                        """)
                .param("userId", user.getId())
                .param("orderNumber", "ORD-AI-" + System.nanoTime())
                .query(Long.class)
                .single();
        insertOrderItem(orderId, productIdA);
        insertOrderItem(orderId, productIdB);
    }

    private void insertOrderItem(Long orderId, Long productId) {
        jdbcClient.sql("""
                        insert into order_items (order_id, product_id, title, price, quantity, line_total)
                        values (:orderId, :productId, 'item', 10.00, 1, 10.00)
                        """)
                .param("orderId", orderId)
                .param("productId", productId)
                .update();
    }
}
