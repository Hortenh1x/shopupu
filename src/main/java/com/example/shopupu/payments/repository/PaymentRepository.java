package com.example.shopupu.payments.repository;

import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;


/**
 * describes the PaymentRepository interface.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {


    Optional<Payment> findTopByOrderOrderByCreatedAtDesc(Order order);

    List<Payment> findByOrder(Order order);


    Optional<Payment> findByExternalId(String externalId);


    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}