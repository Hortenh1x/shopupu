package com.example.shopupu.payments.repository;

import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * RU: Репозиторий для платежей.
 * EN: Repository for payment entities.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * RU: Находим платёж по заказу.
     * EN: Find payment by order.
     */
    Optional<Payment> findByOrder(Order order);

    /**
     * RU: Находим платёж по внешнему ID (например, ID из Stripe).
     * EN: Find payment by external ID (from provider).
     */
    Optional<Payment> findByExternalId(String externalId);

    /**
     * RU: Находим платёж по идемпотентному ключу.
     * EN: Find payment by idempotency key.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
