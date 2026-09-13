package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.common.audit.AuditService;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.support.PostgresContainerSupport;
import com.zaxxer.hikari.HikariDataSource;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** One connection makes nested audit transactions fail deterministically, without load generation. */
// The marker property keeps this context out of the shared cache: the pool below is narrowed in place.
@SpringBootTest(properties = "shopupu.test.audit-single-connection=true")
@ActiveProfiles("test")
class AuthAuditTransactionIT extends PostgresContainerSupport {
    private static final String PASSWORD = "A quiet audit meadow in 2026";
    @Autowired HikariDataSource dataSource;
    @Autowired AuthService auth;
    @Autowired AuthSessionService sessions;
    @Autowired UserService users;
    @Autowired AuditService audit;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void narrowPoolToOneConnection() throws InterruptedException {
        // Startup (Flyway, schema validation) legitimately needs several connections; the guarantee under
        // test concerns request handling, so the pool is shrunk only once the context is up.
        var config = dataSource.getHikariConfigMXBean();
        config.setConnectionTimeout(500);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(1);
        var pool = dataSource.getHikariPoolMXBean();
        pool.softEvictConnections();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (pool.getTotalConnections() > 1) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Hikari pool did not shrink to one connection: " + pool.getTotalConnections());
            }
            Thread.sleep(50);
        }
    }

    @Test
    void successfulLoginAndAuditUseOneConnection() {
        User user = customer();
        assertEquals("AUTHENTICATED", auth.login(user.getEmail(), PASSWORD).status());
        assertEquals(1, auditCount(user.getEmail(), "LOGIN_SUCCEEDED"));
        assertEquals(1, refreshCount(user));
    }

    @Test
    void failedLoginAuditCommitsAfterRollbackAndContainsNoRawEmail() {
        User user = customer();
        assertThrows(UnauthorizedException.class, () -> auth.login(user.getEmail(), "incorrect"));
        assertEquals(1, auditCount(AuditService.accountActor(user.getEmail()), "LOGIN_FAILED"));
        assertEquals(0, auditCount(user.getEmail(), "LOGIN_FAILED"));
        assertEquals(0, refreshCount(user));
    }

    @Test
    void rollbackDiscardsBothSessionAndSuccessAudit() {
        User user = customer();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            assertEquals("AUTHENTICATED", sessions.complete(user, null, null, "LOCAL").status());
            tx.setRollbackOnly();
        });
        assertEquals(0, auditCount(user.getEmail(), "LOGIN_SUCCEEDED"));
        assertEquals(0, refreshCount(user));
    }

    @Test
    void auditPersistenceFailureFailsClosedAndRollsBackTheBusinessChange() {
        User user = customer();
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            sessions.complete(user, null, null, "LOCAL");
            // Synthetic invalid audit fixture exceeds the schema's 64-character event type limit.
            audit.record(user.getEmail(), "x".repeat(65), "user", user.getId().toString(), null);
        }));
        assertEquals(0, auditCount(user.getEmail(), "LOGIN_SUCCEEDED"));
        assertEquals(0, refreshCount(user));
    }

    private User customer() {
        return users.registerUser("audit-" + System.nanoTime() + "@example.test", PASSWORD);
    }
    private long auditCount(String actor, String kind) {
        return jdbc.sql("select count(*) from audit_events where actor = :actor and event_type = :kind")
                .param("actor", actor).param("kind", kind).query(Long.class).single();
    }
    private long refreshCount(User user) {
        return jdbc.sql("select count(*) from refresh_tokens where user_id = :id")
                .param("id", user.getId()).query(Long.class).single();
    }
}
