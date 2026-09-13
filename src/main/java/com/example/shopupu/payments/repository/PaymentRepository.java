package com.example.shopupu.payments.repository;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.payments.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * describes the PaymentRepository interface.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @org.springframework.data.jpa.repository.Query(value = """
            select count(*) from pg_advisory_xact_lock(hashtextextended('payment-create:' || :key, 0))
            """, nativeQuery = true)
    Long lockIdempotencyKey(String key);

    @org.springframework.data.jpa.repository.Query(value = """
            select exists(select 1 from orders o join users u on u.id = o.user_id
                where o.id = :orderId and u.enabled = true and u.deleted_at is null)
            """, nativeQuery = true)
    boolean isOrderOwnerActive(Long orderId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.id = :id")
    Optional<Payment> findLockedById(Long id);

    @org.springframework.data.jpa.repository.Query("select p.order.id from Payment p where p.id = :id")
    Optional<Long> findOrderIdByPaymentId(Long id);

    @org.springframework.data.jpa.repository.Query("select p.id from Payment p where p.externalId = :externalId")
    Optional<Long> findIdByExternalId(String externalId);

    @org.springframework.data.jpa.repository.Query(value = """
            select id from payments where provider = 'stub' and status in ('CREATED', 'PENDING')
                and created_at < :cutoff order by id limit 100
            """, nativeQuery = true)
    List<Long> findStaleUnfinishedIds(java.time.Instant cutoff);

    @org.springframework.data.jpa.repository.Query("""
            select p from Payment p where p.provider = :provider and p.status = com.example.shopupu.payments.entity.PaymentStatus.CREATED
                and p.externalId is null and p.createdAt < :before and p.id > :afterId order by p.id
            """)
    List<Payment> findCreateRecoveryCandidates(String provider, java.time.Instant before, Long afterId,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select p from Payment p where p.provider = :provider and p.refundStatus in :statuses
                and p.id > :afterId order by p.id
            """)
    List<Payment> findRefundCandidates(String provider,
            java.util.Collection<com.example.shopupu.payments.gateway.PaymentGatewayRefundStatus> statuses,
            Long afterId, org.springframework.data.domain.Pageable pageable);



    Optional<Payment> findTopByOrderOrderByCreatedAtDesc(Order order);

    List<Payment> findByOrder(Order order);


    Optional<Payment> findByExternalId(String externalId);


    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findTop100ByStatusInAndCreatedAtBefore(
            java.util.Collection<com.example.shopupu.payments.entity.PaymentStatus> statuses,
            java.time.Instant cutoff);

    @org.springframework.data.jpa.repository.Query("""
            select new com.example.shopupu.payments.dto.PaymentReconciliationCandidate(
                p.id, p.order.id, p.provider, p.externalId, p.amount, p.currency, p.status)
            from Payment p where p.provider = :provider and p.status in :statuses
                and p.externalId is not null and p.createdAt between :createdAfter and :createdBefore
                and p.id > :afterId order by p.id
            """)
    List<com.example.shopupu.payments.dto.PaymentReconciliationCandidate> findReconciliationCandidates(
            String provider, java.util.Collection<com.example.shopupu.payments.entity.PaymentStatus> statuses,
            java.time.Instant createdAfter, java.time.Instant createdBefore, Long afterId,
            org.springframework.data.domain.Pageable pageable);

    List<Payment> findTop100ByStatusInAndExternalIdIsNotNullAndCreatedAtBetween(
            java.util.Collection<com.example.shopupu.payments.entity.PaymentStatus> statuses,
            java.time.Instant createdAfter,
            java.time.Instant createdBefore);
}
