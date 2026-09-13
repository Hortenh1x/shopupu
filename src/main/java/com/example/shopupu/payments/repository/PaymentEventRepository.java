package com.example.shopupu.payments.repository;

import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentEvent;
import com.example.shopupu.payments.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * describes the PaymentEventRepository interface.
 */
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            insert into payment_events (payment_id, external_event_id, new_status, source, details)
            values (:paymentId, :eventId, :status, :source, :details)
            on conflict (external_event_id) do nothing
            """, nativeQuery = true)
    int insertCallbackEvent(Long paymentId, String eventId, String status, String source, String details);

    List<PaymentEvent> findByPayment(Payment payment);
    List<PaymentEvent> findByNewStatus(PaymentStatus status);
    Optional<PaymentEvent> findByExternalEventId(String externalEventId);
}
