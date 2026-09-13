package com.example.shopupu.identity.repository;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Explicit allowlists for the local data export and transactional pseudonymization. */
@Repository
@RequiredArgsConstructor
public class PersonalDataRepository {
    private final JdbcClient jdbc;

    private static final String OWN_ORDERS = "select id from orders where user_id = :userId";
    private static final String OWN_PAYMENTS = "select id from payments where order_id in (" + OWN_ORDERS + ")";
    private static final String RELATED_AUDIT = """
            (lower(actor) = lower(:email) or actor = :username or actor = :accountHash
             or (target_type = 'user' and target_id in (cast(:userId as text), :email))
             or (target_type = 'order' and target_id in (select cast(id as text) from orders where user_id = :userId))
             or (target_type = 'payment' and target_id in
                 (select cast(p.id as text) from payments p join orders o on o.id = p.order_id where o.user_id = :userId))
             or (target_type = 'review' and target_id in (select cast(id as text) from reviews where user_id = :userId)))
            """;

    public Map<String, List<Map<String, Object>>> exportRecords(long userId, String email, String username) {
        Map<String, List<Map<String, Object>>> records = new LinkedHashMap<>();
        put(records, "account", "select id, username, auth_provider, auth_version, mfa_last_accepted_step, deleted_at, (mfa_secret_ciphertext is not null) as mfa_enabled from users where id = :userId", userId);
        put(records, "cart", "select c.id as cart_id, c.created_at, c.updated_at, i.id as item_id, i.variant_id, i.quantity, i.created_at as item_created_at, i.updated_at as item_updated_at from carts c left join cart_items i on i.cart_id = c.id where c.user_id = :userId order by i.id", userId);
        put(records, "reviewDetails", "select id, product_id, order_id, rating, body, status, source, created_at, updated_at from reviews where user_id = :userId order by id", userId);
        put(records, "inventoryHistory", "select variant_id, movement_type, quantity, reference, created_at from inventory_movements where reference in (select 'order:' || order_number from orders where user_id = :userId) order by id", userId);
        put(records, "wishlist", "select id, product_id, created_at from wishlist_items where user_id = :userId order by id", userId);
        put(records, "consents", "select id, consent_type, granted, policy_version, created_at from user_consents where user_id = :userId order by id", userId);
        put(records, "orderDetails", "select id, order_number, status, subtotal_amount, shipping_amount, discount_amount, payment_amount, promo_code, idempotency_key, created_at, updated_at from orders where user_id = :userId order by id", userId);
        put(records, "orderItems", "select id, order_id, product_id, variant_id, title, sku, size, color, brand, price, quantity, line_total from order_items where order_id in (" + OWN_ORDERS + ") order by id", userId);
        records.put("orderHistory", scoped("select id, order_id, from_status, to_status, case when lower(changed_by) = lower(:email) or changed_by = :username then changed_by else '[staff/system]' end as changed_by, created_at from order_status_history where order_id in (" + OWN_ORDERS + ") order by id", userId, email, username).query().listOfRows());
        put(records, "shipments", """
                select s.id, s.order_id, s.method, s.status, s.cost, s.currency, s.tracking_number,
                       s.address_snapshot, s.created_at, s.updated_at,
                       a.full_name, a.line1, a.line2, a.city, a.state, a.postal_code, a.country
                from shipments s left join shipping_addresses a on a.id = s.address_id
                where s.order_id in (select id from orders where user_id = :userId) order by s.id
                """, userId);
        put(records, "payments", "select id, order_id, provider, external_id, amount, currency, status, idempotency_key, refund_operation_key, refund_status, refund_external_id, created_at, updated_at from payments where id in (" + OWN_PAYMENTS + ") order by id", userId);
        put(records, "paymentEvents", "select payment_id, external_event_id, new_status, details, created_at from payment_events where payment_id in (" + OWN_PAYMENTS + ") order by id", userId);
        put(records, "refundAttempts", "select operation_key, payment_id, provider, status, external_refund_id, created_at, updated_at from payment_refund_attempts where payment_id in (" + OWN_PAYMENTS + ") order by created_at, operation_key", userId);
        put(records, "promoRedemptions", "select r.order_id, p.code, p.promo_type, p.value, r.created_at from promo_redemptions r join promo_codes p on p.id = r.promo_id where r.user_id = :userId order by r.id", userId);
        records.put("audit", scoped("select id, case when lower(actor) = lower(:email) or actor = :username or actor = :accountHash then actor else '[staff/system]' end as actor, event_type, target_type, target_id, details, created_at from audit_events where " + RELATED_AUDIT + " order by id", userId, email, username).query().listOfRows());
        put(records, "sessions", "select created_at, expires_at, revoked, auth_version, mfa_verified_at from refresh_tokens where user_id = :userId order by id", userId);
        put(records, "oneTimeTokens", "select purpose, created_at, expires_at, used_at from one_time_tokens where user_id = :userId order by id", userId);
        put(records, "mfaChallenges", "select kind, expires_at, used_at, attempts, login_method from mfa_challenges where user_id = :userId order by id", userId);
        put(records, "mfaRecovery", "select used_at from mfa_recovery_codes where user_id = :userId order by id", userId);
        return records;
    }

    /** Called after the user lock; serializes delivery edits and financial transitions. */
    public void lockOrders(long userId) {
        jdbc.sql("select id from orders where user_id = :userId order by id for update")
                .param("userId", userId).query(Long.class).list();
    }

    public void eraseRelatedData(long userId, String email, String username) {
        List<Long> addresses = jdbc.sql("select address_id from shipments where order_id in (" + OWN_ORDERS + ") and address_id is not null")
                .param("userId", userId).query(Long.class).list();
        update("update shipments set address_id = null, address_snapshot = null, tracking_number = null where order_id in (" + OWN_ORDERS + ")", userId);
        for (Long address : addresses) {
            // A legacy shared address must remain for another customer's shipment.
            jdbc.sql("delete from shipping_addresses where id = :id and not exists (select 1 from shipments where address_id = :id)")
                    .param("id", address).update();
        }
        for (String table : List.of("user_addresses", "wishlist_items", "carts", "user_consents",
                "refresh_tokens", "one_time_tokens", "mfa_challenges", "mfa_recovery_codes")) {
            update("delete from " + table + " where user_id = :userId", userId);
        }
        scoped("update order_status_history set changed_by = :replacement where lower(changed_by) = lower(:email) or changed_by = :username", userId, email, username)
                .param("replacement", "deleted-user:" + userId).update();
        update("update payments set client_secret = null, client_token = null, payment_url = null where id in (" + OWN_PAYMENTS + ")", userId);
        update("update payment_events set source = 'RETAINED_HISTORY', details = null where payment_id in (" + OWN_PAYMENTS + ")", userId);
        scoped("""
                update audit_events set
                    actor = case when lower(actor) = lower(:email) or actor = :username or actor = :accountHash then :replacement else actor end,
                    target_id = case when target_type = 'user' and target_id = :email then cast(:userId as text) else target_id end,
                    details = null
                where
                """ + RELATED_AUDIT, userId, email, username).param("replacement", "deleted-user:" + userId).update();

    }

    /** Flush review mutations first: the shared review->product lock order avoids stale AI writes. */
    public void deleteReviewSummaries(List<Long> productIds) {
        for (Long productId : productIds) {
            jdbc.sql("select id from products where id = :id for update").param("id", productId).query(Long.class).optional();
            jdbc.sql("delete from product_review_summary where product_id = :id").param("id", productId).update();
        }
    }

    private void put(Map<String, List<Map<String, Object>>> out, String section, String sql, long userId) {
        out.put(section, jdbc.sql(sql).param("userId", userId).query().listOfRows());
    }

    private void update(String sql, long userId) { jdbc.sql(sql).param("userId", userId).update(); }

    private JdbcClient.StatementSpec scoped(String sql, long userId, String email, String username) {
        return jdbc.sql(sql).param("userId", userId).param("email", email)
                .param("username", username, Types.VARCHAR)
                .param("accountHash", com.example.shopupu.common.audit.AuditService.accountActor(email));
    }
}
