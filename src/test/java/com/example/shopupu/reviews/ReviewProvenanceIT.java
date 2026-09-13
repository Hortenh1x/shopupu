package com.example.shopupu.reviews;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.shopupu.reviews.entity.ReviewSource;
import com.example.shopupu.reviews.mapper.ReviewMapper;
import com.example.shopupu.reviews.repository.ReviewRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewProvenanceIT extends PostgresContainerSupport {
    @Autowired JdbcClient jdbc;
    @Autowired ReviewRepository reviews;
    @Autowired ReviewMapper mapper;

    @Test
    void additiveBackfillRecognizesDocumentedSeedOnlyAndApiRetainsProvenance() throws Exception {
        String key = UUID.randomUUID().toString();
        long category = id("insert into categories(name,slug) values('Provenance',?) returning id", key);
        jdbc.sql("insert into products(id,title,slug,price,category_id) values(1,'Fixture',?,10,?) on conflict(id) do nothing")
                .params(key, category).update();
        long seedUser = id("insert into users(email,password_hash) values('demo-review-1-001@shopupu.local','!synthetic-review-author-no-login!') returning id");
        long ordinaryUser = id("insert into users(email,password_hash) values('demo-review-1-002@shopupu.local','real-password-hash') returning id");
        long seedReview = id("insert into reviews(user_id,product_id,rating,body,status) values(?,1,5,'synthetic','APPROVED') returning id", seedUser);
        long ordinaryReview = id("insert into reviews(user_id,product_id,rating,body,status) values(?,1,5,'submitted','APPROVED') returning id", ordinaryUser);
        String migration = new ClassPathResource("db/migration/V23__review_provenance.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String backfill = migration.substring(migration.indexOf("with seed_author"), migration.indexOf("-- END KNOWN SYNTHETIC BACKFILL"));
        assertEquals(317, backfill.lines().filter(line -> line.contains("@shopupu.local'")).count());
        jdbc.sql(backfill).update();
        assertEquals(ReviewSource.SYNTHETIC_DEMO, mapper.toResponse(reviews.findById(seedReview).orElseThrow()).source());
        assertEquals(ReviewSource.SYNTHETIC_DEMO, mapper.toAdminResponse(reviews.findById(seedReview).orElseThrow()).source());
        assertEquals(ReviewSource.UNKNOWN, mapper.toResponse(reviews.findById(ordinaryReview).orElseThrow()).source());
    }

    private long id(String sql, Object... args) { return jdbc.sql(sql).params(args).query(Long.class).single(); }
}
