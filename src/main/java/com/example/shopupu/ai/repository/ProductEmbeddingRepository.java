package com.example.shopupu.ai.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain-SQL access to product_embeddings (V15). Deliberately not a JPA entity:
 * the pgvector column type stays out of the Hibernate model, so ddl validate
 * and OSIV concerns never apply; KNN needs native SQL anyway.
 */
@Repository
@RequiredArgsConstructor
public class ProductEmbeddingRepository {

    private final JdbcClient jdbcClient;

    public void upsert(Long productId, String model, float[] embedding) {
        jdbcClient.sql("""
                        insert into product_embeddings (product_id, model, dim, embedding, updated_at)
                        values (:productId, :model, :dim, cast(:embedding as vector), now())
                        on conflict (product_id) do update set
                            model = excluded.model,
                            dim = excluded.dim,
                            embedding = excluded.embedding,
                            updated_at = excluded.updated_at
                        """)
                .param("productId", productId)
                .param("model", model)
                .param("dim", embedding.length)
                .param("embedding", toVectorLiteral(embedding))
                .update();
    }

    public void deleteByProductId(Long productId) {
        jdbcClient.sql("delete from product_embeddings where product_id = :productId")
                .param("productId", productId)
                .update();
    }

    /** KNN over sellable products only; <=> is cosine distance, matching the HNSW index. */
    public List<Long> findNearestProductIds(float[] queryEmbedding, String model, int limit) {
        return jdbcClient.sql("""
                        select pe.product_id
                        from product_embeddings pe
                        join products p on p.id = pe.product_id
                        where pe.model = :model and p.enabled = true and p.deleted_at is null
                        order by pe.embedding <=> cast(:embedding as vector)
                        limit :limit
                        """)
                .param("model", model)
                .param("embedding", toVectorLiteral(queryEmbedding))
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    /** Same KNN but with the cosine distance, so callers can judge relevance. */
    public List<ScoredProductId> findNearestProductIdsWithDistance(float[] queryEmbedding, String model, int limit) {
        return jdbcClient.sql("""
                        select pe.product_id, pe.embedding <=> cast(:embedding as vector) as distance
                        from product_embeddings pe
                        join products p on p.id = pe.product_id
                        where pe.model = :model and p.enabled = true and p.deleted_at is null
                        order by distance
                        limit :limit
                        """)
                .param("model", model)
                .param("embedding", toVectorLiteral(queryEmbedding))
                .param("limit", limit)
                .query((rs, rowNum) -> new ScoredProductId(rs.getLong("product_id"), rs.getDouble("distance")))
                .list();
    }

    public record ScoredProductId(Long productId, double distance) {
    }

    /** Nearest neighbours of an already-indexed product, excluding itself. */
    public List<Long> findSimilarProductIds(Long productId, String model, int limit) {
        return jdbcClient.sql("""
                        select candidate.product_id
                        from product_embeddings anchor
                        join product_embeddings candidate
                            on candidate.product_id <> anchor.product_id and candidate.model = anchor.model
                        join products p on p.id = candidate.product_id
                        where anchor.product_id = :productId and anchor.model = :model
                            and p.enabled = true and p.deleted_at is null
                        order by candidate.embedding <=> anchor.embedding
                        limit :limit
                        """)
                .param("productId", productId)
                .param("model", model)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    /** Keyset page of sellable products still lacking an embedding for this model. */
    public List<Long> findProductIdsMissingEmbedding(String model, Long afterProductId, int limit) {
        return jdbcClient.sql("""
                        select p.id
                        from products p
                        left join product_embeddings pe on pe.product_id = p.id and pe.model = :model
                        where p.enabled = true and p.deleted_at is null
                            and pe.product_id is null and p.id > :afterProductId
                        order by p.id
                        limit :limit
                        """)
                .param("model", model)
                .param("afterProductId", afterProductId)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
