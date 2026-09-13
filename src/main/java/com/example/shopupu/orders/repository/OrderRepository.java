package com.example.shopupu.orders.repository;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Serialize checkout for one user, including the first insertion and cart consumption.
    @org.springframework.data.jpa.repository.Query(value = """
            select count(*) from pg_advisory_xact_lock(hashtextextended('checkout-user:' || :userId, 0))
            """, nativeQuery = true)
    Long lockCheckoutUser(Long userId);

    // All order/payment/shipping mutations acquire this lock before payment and inventory locks.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id")
    Optional<Order> findLockedById(Long id);

    @org.springframework.data.jpa.repository.Query(value = """
            select exists(select 1 from payments where order_id = :orderId
                and (status in ('CREATED', 'PENDING') or refund_status in ('PENDING', 'UNKNOWN')))
            """, nativeQuery = true)
    boolean hasUnsettledPayment(Long orderId);

    @org.springframework.data.jpa.repository.Query(value = """
            select o.id from orders o where o.status in ('CREATED', 'PENDING_PAYMENT')
                and o.created_at < :cutoff
                and not exists (select 1 from payments p where p.order_id = o.id
                    and (p.status in ('CREATED', 'PENDING') or p.refund_status in ('PENDING', 'UNKNOWN')))
                order by o.id limit 100
            """, nativeQuery = true)
    List<Long> findStaleUnpaidIds(Instant cutoff);


    Page<Order> findByUser(User user, Pageable pageable);

    Page<Order> findByUserAndStatus(User user, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findWithItemsById(Long id);

    Optional<Order> findByUserAndIdempotencyKey(User user, String idempotencyKey);

    boolean existsByOrderNumber(String orderNumber);

    boolean existsByUserAndStatusInAndItems_ProductId(User user, Collection<OrderStatus> statuses, Long productId);

    List<Order> findTop100ByStatusInAndCreatedAtBefore(Collection<OrderStatus> statuses, Instant cutoff);
}
