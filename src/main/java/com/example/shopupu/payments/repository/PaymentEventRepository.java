package com.example.shopupu.payments.repository;

import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentEvent;
import com.example.shopupu.payments.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * RU: Репозиторий событий платежей.
 * EN: Repository for payment events.
 */
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    List<PaymentEvent> findByPayment(Payment payment);
    List<PaymentEvent> findByNewStatus(PaymentStatus status);
}
