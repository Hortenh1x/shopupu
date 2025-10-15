package com.example.shopupu.payments.service;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.payments.dto.PaymentEventDto;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.entity.*;
import com.example.shopupu.payments.provider.PaymentProvider;
import com.example.shopupu.payments.provider.PaymentProviderFactory;
import com.example.shopupu.payments.repository.PaymentEventRepository;
import com.example.shopupu.payments.repository.PaymentRepository;
import com.example.shopupu.payments.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * RU: Основной сервис работы с платежами.
 * EN: Main business service for payments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentProviderFactory providerFactory;
    private final PaymentMapper paymentMapper;

    /**
     * RU: Создаёт новый платёж для заказа.
     * EN: Creates a new payment for the given order.
     */
    @Transactional
    public PaymentResponse createPayment(Long orderId, String providerName) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        PaymentProvider provider = providerFactory.getProvider(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown payment provider: " + providerName);
        }

        // 1️⃣ Создаём платёж в системе провайдера (Stripe, PayPal и т.д.)
        PaymentResponse externalResponse = provider.createPayment(order);

        // 2️⃣ Создаём локальную запись в БД
        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .provider(providerName)
                .status(externalResponse.status()) // enum PaymentStatus
                .externalId(externalResponse.externalId())
                .clientSecret(externalResponse.clientSecret())
                .build();

        paymentRepository.save(payment);

        // 3️⃣ Логируем событие
        recordEvent(payment, payment.getStatus(), "SYSTEM", "Payment created");

        return paymentMapper.toResponse(payment);
    }

    /**
     * RU: Обработка входящего webhook-а от провайдера.
     * EN: Handles incoming webhook from payment provider.
     */
    @Transactional
    public void handleWebhook(String providerName, String payload, String signature) {
        PaymentProvider provider = providerFactory.getProvider(providerName);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown provider: " + providerName);
        }

        Optional<PaymentEventDto> eventOpt = provider.parseWebhook(payload, signature);
        if (eventOpt.isEmpty()) {
            log.warn("⚠️ Skipped unknown webhook from {}", providerName);
            return;
        }

        PaymentEventDto eventData = eventOpt.get();
        String externalId = eventData.externalPaymentId();
        PaymentStatus newStatus = eventData.status();

        // 1️⃣ Проверка на идемпотентность (если уже был такой статус — пропускаем)
        Payment payment = paymentRepository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for webhook: " + externalId));

        if (payment.getStatus() == newStatus) {
            log.info("✅ Duplicate webhook ignored for payment {}", externalId);
            return;
        }

        // 2️⃣ Обновляем статус платежа
        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        // 3️⃣ Записываем событие
        recordEvent(payment, newStatus, providerName.toUpperCase() + "_WEBHOOK", payload);

        // 4️⃣ Обновляем статус заказа (например, при успешной оплате)
        if (newStatus == PaymentStatus.SUCCEEDED) {
            orderRepository.updateStatus(payment.getOrder().getId(), "PAID");
            log.info("💰 Order {} marked as PAID", payment.getOrder().getId());
        }
    }

    /**
     * RU: Запись события в историю платежей.
     * EN: Records event in payment history.
     */
    private void recordEvent(Payment payment, PaymentStatus status, String source, String details) {
        PaymentEvent event = PaymentEvent.builder()
                .payment(payment)
                .newStatus(status)
                .source(source)
                .details(details)
                .build();
        paymentEventRepository.save(event);
    }

    /**
     * RU: Возвращает платёж по ID (для UI или админки).
     * EN: Returns payment by ID.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        return paymentMapper.toResponse(payment);
    }
}
