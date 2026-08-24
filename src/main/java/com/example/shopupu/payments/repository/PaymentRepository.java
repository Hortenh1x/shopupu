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


    Optional<Payment> findTopByOrderOrderByCreatedAtDesc(Order order);

    List<Payment> findByOrder(Order order);


    Optional<Payment> findByExternalId(String externalId);


    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findTop100ByStatusInAndCreatedAtBefore(
            java.util.Collection<com.example.shopupu.payments.entity.PaymentStatus> statuses,
            java.time.Instant cutoff);

    List<Payment> findTop100ByStatusInAndExternalIdIsNotNullAndCreatedAtBetween(
            java.util.Collection<com.example.shopupu.payments.entity.PaymentStatus> statuses,
            java.time.Instant createdAfter,
            java.time.Instant createdBefore);
}
