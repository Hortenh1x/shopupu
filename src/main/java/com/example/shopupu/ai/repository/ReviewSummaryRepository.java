package com.example.shopupu.ai.repository;

import com.example.shopupu.ai.dto.ReviewSummaryResponse;
import com.example.shopupu.ai.model.ReviewSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain-SQL access to product_review_summary (V17): jsonb pros/cons stay out
 * of the Hibernate model, mirroring ProductEmbeddingRepository.
 */
@Repository
@RequiredArgsConstructor
public class ReviewSummaryRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public void upsert(Long productId, ReviewSummary summary, int basedOnReviews, String model) {
        jdbcClient.sql("""
                        insert into product_review_summary
                            (product_id, tldr, pros, cons, sentiment, based_on_reviews, model, generated_at)
                        values (:productId, :tldr, cast(:pros as jsonb), cast(:cons as jsonb),
                                :sentiment, :basedOnReviews, :model, now())
                        on conflict (product_id) do update set
                            tldr = excluded.tldr,
                            pros = excluded.pros,
                            cons = excluded.cons,
                            sentiment = excluded.sentiment,
                            based_on_reviews = excluded.based_on_reviews,
                            model = excluded.model,
                            generated_at = excluded.generated_at
                        """)
                .param("productId", productId)
                .param("tldr", summary.tldr())
                .param("pros", writeJson(summary.pros()))
                .param("cons", writeJson(summary.cons()))
                .param("sentiment", summary.sentiment() == null
                        ? ReviewSummary.Sentiment.MIXED.name()
                        : summary.sentiment().name())
                .param("basedOnReviews", basedOnReviews)
                .param("model", model)
                .update();
    }

    public Optional<ReviewSummaryResponse> findByProductId(Long productId) {
        return jdbcClient.sql("""
                        select product_id, tldr, pros, cons, sentiment, based_on_reviews, generated_at
                        from product_review_summary
                        where product_id = :productId
                        """)
                .param("productId", productId)
                .query(this::mapRow)
                .optional();
    }

    public void deleteByProductId(Long productId) {
        jdbcClient.sql("delete from product_review_summary where product_id = :productId")
                .param("productId", productId)
                .update();
    }

    private ReviewSummaryResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewSummaryResponse(
                rs.getLong("product_id"),
                rs.getString("tldr"),
                readJsonList(rs.getString("pros")),
                readJsonList(rs.getString("cons")),
                rs.getString("sentiment"),
                rs.getInt("based_on_reviews"),
                rs.getObject("generated_at", OffsetDateTime.class).toInstant()
        );
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize review summary list", ex);
        }
    }

    private List<String> readJsonList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize review summary list", ex);
        }
    }
}
