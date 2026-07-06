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
