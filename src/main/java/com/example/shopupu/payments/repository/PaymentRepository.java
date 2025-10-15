package com.example.shopupu.payments.repository;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.payments.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // RU: все платежи заказа / EN: all payments of an order
    List<Payment> findByOrder(Order order);
    Optional<Payment> findByIdempotencyKey(String key);

    // RU: поиск по id платежа на стороне провайдера / EN: lookup by provider payment id
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
}
