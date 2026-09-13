package com.example.shopupu.payments.repository;

import com.example.shopupu.payments.entity.PaymentRefundAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundAttemptRepository extends JpaRepository<PaymentRefundAttempt, String> {}
