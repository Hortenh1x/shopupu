package com.example.shopupu.ai.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain-SQL access to product_recommendations (V16). Co-occurrence is computed
 * entirely in PostgreSQL from paid orders — no LLM involved.
 */
@Repository
@RequiredArgsConstructor
public class ProductRecommendationRepository {

    public static final String KIND_BOUGHT_TOGETHER = "BOUGHT_TOGETHER";

    /** Order states that prove the products were actually bought (matches ReviewService). */
    private static final String PURCHASED_STATUSES =
            "('PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'COMPLETED')";

    private final JdbcClient jdbcClient;

    /** Full rebuild in one transaction: readers keep the old pairs until commit. */
    @Transactional
    public int recomputeBoughtTogether(int topPerProduct, int minPairCount) {
        jdbcClient.sql("delete from product_recommendations where kind = '" + KIND_BOUGHT_TOGETHER + "'")
                .update();
        return jdbcClient.sql("""
                        insert into product_recommendations
                            (product_id, recommended_product_id, kind, score, computed_at)
                        select product_id, recommended_product_id, '%s', pair_count, now()
                        from (
                            select a.product_id                as product_id,
                                   b.product_id                as recommended_product_id,
                                   count(*)                    as pair_count,
                                   row_number() over (partition by a.product_id
                                                      order by count(*) desc, b.product_id) as rn
                            from order_items a
                            join order_items b on b.order_id = a.order_id and b.product_id <> a.product_id
                            join orders o on o.id = a.order_id
                            where o.status in %s
                            group by a.product_id, b.product_id
                        ) pairs
                        where rn <= :topPerProduct and pair_count >= :minPairCount
                        """.formatted(KIND_BOUGHT_TOGETHER, PURCHASED_STATUSES))
                .param("topPerProduct", topPerProduct)
                .param("minPairCount", minPairCount)
                .update();
    }

    public List<Long> findRecommendedProductIds(Long productId, String kind, int limit) {
        return jdbcClient.sql("""
                        select pr.recommended_product_id
                        from product_recommendations pr
                        join products p on p.id = pr.recommended_product_id
                        where pr.product_id = :productId and pr.kind = :kind
                            and p.enabled = true and p.deleted_at is null
                        order by pr.score desc, pr.recommended_product_id
                        limit :limit
                        """)
                .param("productId", productId)
                .param("kind", kind)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    /** Fallback when no pairs exist yet: best sellers from the same category. */
    public List<Long> findPopularInSameCategory(Long productId, int limit) {
        return jdbcClient.sql("""
                        select oi.product_id
                        from order_items oi
                        join orders o on o.id = oi.order_id and o.status in %s
                        join products p on p.id = oi.product_id
                            and p.enabled = true and p.deleted_at is null
                        where p.category_id = (select category_id from products where id = :productId)
                            and oi.product_id <> :productId
                        group by oi.product_id
                        order by count(*) desc, oi.product_id
                        limit :limit
                        """.formatted(PURCHASED_STATUSES))
                .param("productId", productId)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }
}
