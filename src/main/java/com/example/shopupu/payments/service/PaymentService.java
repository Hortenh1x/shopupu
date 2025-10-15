package com.example.shopupu.payments.service;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.provider.PaymentProvider;
import com.example.shopupu.payments.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider; // подставится FakePaymentProvider

    // RU: Разрешённые переходы статусов платежа — простая state machine
    // EN: Allowed transitions of payment status (simple state machine)
    private static final Map<PaymentStatus, Set<PaymentStatus>> transitions = new EnumMap<>(PaymentStatus.class);

    static {
        transitions.put(PaymentStatus.NEW, Set.of(PaymentStatus.PENDING, PaymentStatus.CANCELED));
        transitions.put(PaymentStatus.PENDING, Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED, PaymentStatus.CANCELED, PaymentStatus.FAILED));
        transitions.put(PaymentStatus.AUTHORIZED, Set.of(PaymentStatus.CAPTURED, PaymentStatus.CANCELED));
        transitions.put(PaymentStatus.CAPTURED, Set.of(PaymentStatus.REFUNDED));
        transitions.put(PaymentStatus.CANCELED, Set.of());
        transitions.put(PaymentStatus.FAILED, Set.of());
        transitions.put(PaymentStatus.REFUNDED, Set.of());
    }

    private void ensureTransition(PaymentStatus from, PaymentStatus to) {
        if (!transitions.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Illegal payment status transition: " + from + " -> " + to);
        }
    }

    /**
     * RU: Создаём платёж для заказа и делаем intent у провайдера.
     * EN: Create payment for order and create intent at provider.
     */
    @Transactional
    public Payment createPayment(Long orderId, String provider, String currency) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order with id " + orderId + " not found"));

        if (order.getStatus() != OrderStatus.NEW) {
            throw new IllegalStateException("Order must be NEW to start payment");
        }

        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .currency(currency)
                .status(PaymentStatus.NEW)
                .provider(provider.toUpperCase())
                .build();

        payment = paymentRepository.save(payment);

        // intent у провайдера
        payment = paymentProvider.createIntent(payment);
        ensureTransition(PaymentStatus.NEW, payment.getStatus());

        return paymentRepository.save(payment);
    }

    /**
     * RU: Подтверждение (например после 3DS). Если CAPTURED — переводим заказ в PAID.
     * EN: Confirm (e.g., after 3DS). If CAPTURED — mark order as PAID.
     */
    @Transactional
    public Payment confirm(String providerPaymentId) {
        Payment existing = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalStateException("Payment with id " + providerPaymentId + " not found"));

        Payment providerState = paymentProvider.confirm(providerPaymentId);
        ensureTransition(existing.getStatus(), providerState.getStatus());

        existing.setStatus(providerState.getStatus());
        Payment saved = paymentRepository.save(existing);

        if (saved.getStatus() == PaymentStatus.CAPTURED) {
            Order order = saved.getOrder();
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
        }
        return saved;
    }

    /**
     * RU: Отмена платежа.
     * EN: Cancel payment.
     */
    @Transactional
    public Payment cancel(String providerPaymentId) {
        Payment existing = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalStateException("Payment with id " + providerPaymentId + " not found"));
        Payment providerState = paymentProvider.cancel(providerPaymentId);
        ensureTransition(existing.getStatus(), providerState.getStatus());
        existing.setStatus(providerState.getStatus());
        return paymentRepository.save(existing);
    }
}