package com.example.shopupu.identity.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.identity.dto.AddressRequest;
import com.example.shopupu.identity.dto.UserDataExport;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GdprPrivacyIT extends PostgresContainerSupport {
    @Autowired GdprService gdpr;
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;
    @Autowired AddressBookService addresses;
    @Autowired TransactionTemplate transactions;

    @Test
    void exportIncludesStoredPersonalAndFinancialRecordsButNeverAuthenticationSecrets() {
        Fixture f = fixture();
        UserDataExport export = gdpr.exportData(users.findById(f.userId()).orElseThrow());
        assertNotNull(export.profile().gender());
        for (String section : new String[]{"account", "cart", "wishlist", "consents", "orderDetails",
                "orderItems", "orderHistory", "shipments", "payments", "paymentEvents", "refundAttempts",
                "promoRedemptions", "audit", "sessions", "oneTimeTokens", "mfaChallenges", "mfaRecovery"}) {
            assertFalse(export.records().get(section).isEmpty(), section);
        }
        String records = export.records().toString();
        assertTrue(records.contains("PRIVATE-SHIPPING"));
        assertTrue(records.contains("PRIVATE-AUDIT"));
        assertTrue(records.contains("PAY-EXTERNAL"));
        assertTrue(records.contains("POLICY-TEST"));
        assertTrue(records.contains("LOGIN_FAILED"));
        assertFalse(records.contains("SECRET-"));
        assertFalse(records.contains("OTHER-PRIVATE"));
        assertFalse(export.scope().isEmpty());
    }

    @Test
    void erasureRemovesDirectPiiAndSecretsButPreservesFinancialAndInventoryIntegrity() {
        Fixture f = fixture();
        User user = users.findById(f.userId()).orElseThrow();
        gdpr.anonymizeAccount(user);
        users.flush();
        assertNull(user.getGender());
        assertNull(user.getMfaSecretCiphertext());
        assertEquals(-1, user.getMfaLastAcceptedStep());
        assertEquals(1, user.getAuthVersion());
        assertFalse(user.isEnabled());
        assertFalse(user.isEmailVerified());
        assertTrue(user.getRoles().isEmpty());
        for (String table : new String[]{"user_addresses", "wishlist_items", "carts", "user_consents",
                "refresh_tokens", "one_time_tokens", "mfa_challenges", "mfa_recovery_codes"}) {
            assertEquals(0L, count(table, "user_id", f.userId()), table);
        }
        var shipment = jdbc.sql("select address_id, address_snapshot, tracking_number from shipments where order_id = ?")
                .param(f.orderId()).query().singleRow();
        assertNull(shipment.get("address_id"));
        assertNull(shipment.get("address_snapshot"));
        assertNull(shipment.get("tracking_number"));
        assertEquals(0L, count("shipping_addresses", "id", f.addressId()));
        assertEquals("[deleted]", jdbc.sql("select body from reviews where user_id = ?")
                .param(f.userId()).query(String.class).single());
        assertEquals(0L, count("product_review_summary", "product_id", f.productId()));
        assertEquals("PAID", jdbc.sql("select status from orders where id = ?").param(f.orderId()).query(String.class).single());
        assertEquals(new BigDecimal("22.00"), jdbc.sql("select payment_amount from orders where id = ?")
                .param(f.orderId()).query(BigDecimal.class).single());
        assertEquals("SUCCEEDED", jdbc.sql("select status from payments where id = ?")
                .param(f.paymentId()).query(String.class).single());
        var accessFields = jdbc.sql("select client_secret, client_token, payment_url from payments where id = ?")
                .param(f.paymentId()).query().singleRow();
        assertTrue(accessFields.values().stream().allMatch(java.util.Objects::isNull));
        assertEquals(1L, count("payment_refund_attempts", "payment_id", f.paymentId()));
        assertEquals(1L, count("promo_redemptions", "user_id", f.userId()));
        assertEquals(9, jdbc.sql("select stock from inventory where variant_id = ?").param(f.variantId()).query(Integer.class).single());
        assertEquals(2, jdbc.sql("select reserved from inventory where variant_id = ?").param(f.variantId()).query(Integer.class).single());
        String remaining = gdpr.exportData(user).records().toString();
        assertFalse(remaining.contains("PRIVATE-"), remaining);
        assertFalse(remaining.contains(f.email()), remaining);
        assertTrue(remaining.contains("GDPR_ACCOUNT_ERASED"));
        assertEquals("deleted-user:" + f.userId(), jdbc.sql("select changed_by from order_status_history where order_id = ?")
                .param(f.orderId()).query(String.class).single());
        assertEquals(0L, jdbc.sql("select count(*) from audit_events where actor = ? or details = 'PRIVATE-AUDIT'")
                .param(f.email()).query(Long.class).single());
        assertEquals(0L, jdbc.sql("select count(*) from audit_events where actor = ?")
                .param(com.example.shopupu.common.audit.AuditService.accountActor(f.email())).query(Long.class).single());
        assertNull(jdbc.sql("select details from payment_events where payment_id = ?")
                .param(f.paymentId()).query().singleRow().get("details"));
        assertEquals("OTHER-PRIVATE", jdbc.sql("select details from audit_events where actor = ?")
                .param("other-" + f.email()).query(String.class).single());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void admittedAddressWriteCannotRestoreDataAfterErasureCommits() throws Exception {
        Fixture f = transactions.execute(status -> fixture());
        User stale = users.findById(f.userId()).orElseThrow();
        CountDownLatch erasedBeforeCommit = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var erase = executor.submit(() -> transactions.executeWithoutResult(status -> {
                gdpr.anonymizeAccount(stale);
                erasedBeforeCommit.countDown();
                await(releaseCommit);
            }));
            assertTrue(erasedBeforeCommit.await(10, TimeUnit.SECONDS));
            var write = executor.submit(() -> {
                writerStarted.countDown();
                assertThrows(ForbiddenOperationException.class, () -> addresses.addAddress(stale,
                        new AddressRequest("Private name", "Private street", null, "Berlin", null, "12345", "DE", false)));
            });
            assertTrue(writerStarted.await(10, TimeUnit.SECONDS));
            releaseCommit.countDown();
            erase.get(15, TimeUnit.SECONDS);
            write.get(15, TimeUnit.SECONDS);
            assertEquals(0L, count("user_addresses", "user_id", f.userId()));
        } finally {
            releaseCommit.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Timed out awaiting test transaction"); }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private long count(String table, String column, long value) {
        return jdbc.sql("select count(*) from " + table + " where " + column + " = ?")
                .param(value).query(Long.class).single();
    }

    private Fixture fixture() {
        String key = UUID.randomUUID().toString();
        String email = key + "@example.invalid";
        long userId = id("insert into users(email,password_hash,username,first_name,last_name,phone,preferred_size,gender,email_verified,mfa_secret_ciphertext) values(?, 'SECRET-password', ?, 'PRIVATE-first', 'PRIVATE-last', 'PRIVATE-phone', 'M', 'FEMALE',true,'SECRET-mfa') returning id", email, "PRIVATE-" + key);
        jdbc.sql("insert into user_roles(user_id,role_id) select ?,id from roles where name='CUSTOMER'").param(userId).update();
        long category = id("insert into categories(name,slug) values('Test',?) returning id", key);
        long product = id("insert into products(title,slug,price,category_id) values('Tee',?,20,?) returning id", key, category);
        long variant = id("insert into product_variants(product_id,sku,size,color,price) values(?,?,'M','blue',20) returning id", product, key);
        jdbc.sql("insert into inventory(variant_id,stock,reserved) values(?,9,2)").param(variant).update();
        long order = id("insert into orders(user_id,order_number,status,subtotal_amount,shipping_amount,payment_amount) values(?,?,'PAID',20,2,22) returning id", userId, "GDPR-" + key.substring(0, 12));
        jdbc.sql("insert into order_items(order_id,product_id,variant_id,title,price,quantity,line_total) values(?,?,?,'Tee',20,1,20)").params(order, product, variant).update();
        jdbc.sql("insert into order_status_history(order_id,to_status,changed_by) values(?,'PAID',?)").params(order, email).update();
        long address = id("insert into shipping_addresses(full_name,line1,line2,city,state,postal_code,country) values('PRIVATE-name','PRIVATE-SHIPPING','PRIVATE-line2','PRIVATE-city','PRIVATE-state','12345','DE') returning id");
        jdbc.sql("insert into shipments(order_id,address_id,method,status,cost,currency,address_snapshot,tracking_number) values(?,?,'DHL','PENDING',2,'EUR','PRIVATE-SNAPSHOT','PRIVATE-TRACKING')").params(order,address).update();
        jdbc.sql("insert into user_addresses(user_id,full_name,line1,city,postal_code,country) values(?,'PRIVATE-name','PRIVATE-line','PRIVATE-city','12345','DE')").param(userId).update();
        jdbc.sql("insert into wishlist_items(user_id,product_id) values(?,?)").params(userId,product).update();
        long cart = id("insert into carts(user_id) values(?) returning id", userId);
        jdbc.sql("insert into cart_items(cart_id,variant_id,quantity) values(?,?,1)").params(cart,variant).update();
        jdbc.sql("insert into user_consents(user_id,consent_type,granted,policy_version) values(?,'DATA_PROCESSING',true,'POLICY-TEST')").param(userId).update();
        jdbc.sql("insert into reviews(user_id,product_id,rating,body,status,source) values(?,?,5,'PRIVATE-REVIEW','APPROVED','SYNTHETIC_DEMO')").params(userId,product).update();
        jdbc.sql("insert into product_review_summary(product_id,tldr,pros,cons,sentiment,based_on_reviews,model) values(?,'PRIVATE-SUMMARY','[]','[]','POSITIVE',1,'stub')").param(product).update();
        long payment = id("insert into payments(order_id,provider,external_id,amount,currency,status,idempotency_key,client_secret,client_token,payment_url) values(?,'stub',?,22,'EUR','SUCCEEDED',?,'SECRET-client','SECRET-token','SECRET-url') returning id", order, "PAY-EXTERNAL-" + key, key);
        jdbc.sql("insert into payment_events(payment_id,new_status,source,details) values(?,'SUCCEEDED','PRIVATE-ACTOR','PRIVATE-DETAILS')").param(payment).update();
        jdbc.sql("insert into payment_refund_attempts(operation_key,payment_id,provider,status) values(?,?,'stub','FAILED')").params(key,payment).update();
        long promo = id("insert into promo_codes(code,promo_type,value,redemption_count) values(?,'FIXED',1,1) returning id", key);
        jdbc.sql("insert into promo_redemptions(promo_id,user_id,order_id) values(?,?,?)").params(promo,userId,order).update();
        jdbc.sql("insert into audit_events(actor,event_type,target_type,target_id,details) values(?,'PROFILE_CHANGED','user',?,'PRIVATE-AUDIT')").params(email,Long.toString(userId)).update();
        jdbc.sql("insert into audit_events(actor,event_type,target_type,details) values(?,'LOGIN_FAILED','user',null)")
                .param(com.example.shopupu.common.audit.AuditService.accountActor(email)).update();
        jdbc.sql("insert into audit_events(actor,event_type,details) values(?,'OTHER','OTHER-PRIVATE')").param("other-" + email).update();
        jdbc.sql("insert into refresh_tokens(user_id,token,expires_at) values(?,? ,now()+interval '1 day')").params(userId,"SECRET-refresh-"+key).update();
        jdbc.sql("insert into one_time_tokens(user_id,token_hash,purpose,expires_at) values(?,?,'PASSWORD_RESET',now()+interval '1 day')").params(userId,"SECRET-reset-"+key).update();
        jdbc.sql("insert into mfa_challenges(user_id,token_hash,kind,auth_version,expires_at,pending_secret,guest_cart_token,login_method) values(?,?,'ENROLL',0,now()+interval '1 hour','SECRET-pending','SECRET-guest','PASSWORD')").params(userId,"SECRET-challenge-"+key).update();
        jdbc.sql("insert into mfa_recovery_codes(user_id,code_hash) values(?,?)").params(userId,"SECRET-recovery-"+key).update();
        return new Fixture(userId,email,order,address,product,variant,payment);
    }

    private long id(String sql, Object... params) { return jdbc.sql(sql).params(params).query(Long.class).single(); }
    private record Fixture(long userId, String email, long orderId, long addressId, long productId, long variantId, long paymentId) {}
}
