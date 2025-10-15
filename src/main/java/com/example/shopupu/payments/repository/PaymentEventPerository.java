package com.example.shopupu.payments.repository;

import com.example.shopupu.payments.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    Optional<PaymentEvent> findByEventId(String eventId);
}