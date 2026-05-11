package com.example.shopupu.payments.repository;

import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentEvent;
import com.example.shopupu.payments.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


/**
 * describes the PaymentEventRepository interface.
 */
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    List<PaymentEvent> findByPayment(Payment payment);
    List<PaymentEvent> findByNewStatus(PaymentStatus status);
    Optional<PaymentEvent> findByExternalEventId(String externalEventId);
}