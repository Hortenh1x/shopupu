package com.example.shopupu.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shopupu.auth.service.AuthService;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * LEG-06: the privacy page promises concrete retention periods; this proves the nightly job
 * enforces exactly those and nothing more (active, privileged and recently erased accounts
 * are untouched), against a real PostgreSQL with its cascades.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DataRetentionIT extends PostgresContainerSupport {

    private static final Instant NOW = Instant.parse("2028-06-01T04:10:00Z");

    @Autowired DataRetentionJob job;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired JdbcClient jdbc;

    @Test
    void issuingASessionRecordsTheSignInTimeUsedByTheInactivityRule() {
        long id = user("fresh", null, null);
        assertNull(users.findById(id).orElseThrow().getLastLoginAt());

        auth.issueTokens(users.findById(id).orElseThrow());

        Instant recorded = users.findById(id).orElseThrow().getLastLoginAt();
        assertNotNull(recorded);
        assertTrue(recorded.isAfter(Instant.now().minus(1, ChronoUnit.MINUTES)));
    }

    @Test
    void auditEventsOlderThanTwelveMonthsArePurgedAndYoungerOnesStay() {
        String marker = "RET-" + UUID.randomUUID();
        insertAudit(marker, NOW.minus(400, ChronoUnit.DAYS));
        insertAudit(marker, NOW.minus(300, ChronoUnit.DAYS));

        var report = job.run(NOW);

        assertTrue(report.auditEventsPurged() >= 1);
        assertEquals(1, jdbc.sql("select count(*) from audit_events where details = :m").param("m", marker).query(Long.class).single());
        assertEquals(NOW.minus(300, ChronoUnit.DAYS),
                jdbc.sql("select created_at from audit_events where details = :m").param("m", marker)
                        .query(java.time.OffsetDateTime.class).single().toInstant());
    }

    @Test
    void customersWithoutASignInForTwelveMonthsAreErasedLikeAGdprRequest() {
        long inactive = user("inactive", NOW.minus(380, ChronoUnit.DAYS), null);
        long active = user("active", NOW.minus(200, ChronoUnit.DAYS), null);
        long unknown = user("unknown", null, null);
        long admin = user("admin", NOW.minus(380, ChronoUnit.DAYS), null);
        jdbc.sql("insert into user_roles(user_id,role_id) select :id,id from roles where name='ADMIN'").param("id", admin).update();

        var report = job.run(NOW);

        assertTrue(report.inactiveAccountsErased() >= 1);
        var erased = users.findById(inactive).orElseThrow();
        assertNotNull(erased.getDeletedAt(), "the inactive customer is erased");
        assertTrue(erased.getEmail().endsWith("@anonymized.invalid"));
        assertEquals(1, jdbc.sql("select count(*) from audit_events where event_type='RETENTION_INACTIVE_ACCOUNT_ERASED' and target_id = :id")
                .param("id", Long.toString(inactive)).query(Long.class).single());
        assertNull(users.findById(active).orElseThrow().getDeletedAt(), "a recent sign-in keeps the account");
        assertNull(users.findById(unknown).orElseThrow().getDeletedAt(), "an unknown last sign-in is never a candidate");
        assertNull(users.findById(admin).orElseThrow().getDeletedAt(), "privileged accounts are the operator's, not retention's");
    }

    @Test
    void pseudonymisedHistoryOfErasedAccountsIsDroppedAfterTwentyFourMonthsWithItsCascades() {
        long old = user("erased-old", null, NOW.minus(25 * 31, ChronoUnit.DAYS));
        long oldOrder = orderWithPayment(old);
        long recent = user("erased-recent", null, NOW.minus(30, ChronoUnit.DAYS));
        long recentOrder = orderWithPayment(recent);

        var report = job.run(NOW);

        assertTrue(report.erasedAccountsDropped() >= 1);
        assertTrue(users.findById(old).isEmpty(), "the account row itself is gone");
        assertEquals(0, count("orders", "id", oldOrder));
        assertEquals(0, count("payments", "order_id", oldOrder));
        assertEquals(0, count("order_status_history", "order_id", oldOrder));
        assertTrue(users.findById(recent).isPresent(), "a recent erasure keeps its pseudonymised history");
        assertEquals(1, count("orders", "id", recentOrder));
        assertEquals(1, count("payments", "order_id", recentOrder));
    }

    private long user(String prefix, Instant lastLoginAt, Instant deletedAt) {
        return jdbc.sql("insert into users(email,password_hash,enabled,last_login_at,deleted_at) values(:email,'x',:enabled,:last,:deleted) returning id")
                .param("email", prefix + "-" + UUID.randomUUID() + "@example.invalid")
                .param("enabled", deletedAt == null)
                .param("last", ts(lastLoginAt))
                .param("deleted", ts(deletedAt))
                .query(Long.class).single();
    }

    private long orderWithPayment(long userId) {
        String key = UUID.randomUUID().toString();
        long order = jdbc.sql("insert into orders(user_id,order_number,status,subtotal_amount,shipping_amount,payment_amount) values(:u,:n,'PAID',20,0,20) returning id")
                .param("u", userId).param("n", "RET-" + key.substring(0, 12)).query(Long.class).single();
        jdbc.sql("insert into order_status_history(order_id,to_status,changed_by) values(:o,'PAID','retention-test')").param("o", order).update();
        jdbc.sql("insert into payments(order_id,provider,external_id,amount,currency,status,idempotency_key) values(:o,'stub',:e,20,'EUR','SUCCEEDED',:k)")
                .param("o", order).param("e", "PAY-" + key).param("k", key).update();
        return order;
    }

    private void insertAudit(String marker, Instant createdAt) {
        jdbc.sql("insert into audit_events(actor,event_type,details,created_at) values('retention-test','TEST',:m,:at)")
                .param("m", marker).param("at", ts(createdAt)).update();
    }

    private static java.time.OffsetDateTime ts(Instant instant) {
        return instant == null ? null : java.time.OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }

    private long count(String table, String column, long value) {
        return jdbc.sql("select count(*) from " + table + " where " + column + " = :v").param("v", value).query(Long.class).single();
    }
}
