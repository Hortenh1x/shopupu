package com.example.shopupu.payments.service;

import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.payments.dto.PaymentCallbackRequest;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentEvent;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.gateway.PaymentCallbackVerifier;
import com.example.shopupu.payments.gateway.PaymentGatewayClient;
import com.example.shopupu.payments.gateway.PaymentGatewayCreateRequest;
import com.example.shopupu.payments.repository.PaymentEventRepository;
import com.example.shopupu.payments.repository.PaymentRepository;
import com.example.shopupu.payments.mapper.PaymentMapper;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentCallbackVerifier paymentCallbackVerifier;
    private final PaymentProperties paymentProperties;
    private final ShipmentRepository shipmentRepository;
    private final OrderService orderService;
    private final AccessControlService accessControlService;


    @Transactional
    public PaymentResponse createPayment(Long orderId) {
        Order order = findOrder(orderId);
        accessControlService.requireOrderOwnerOrAdmin(order);

        validateOrderCanBePaid(order);
        validatePaymentAttemptAllowed(order);

        String idempotencyKey = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getPaymentAmount())
                .provider(paymentProperties.getDefaultProvider())
                .status(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .currency(paymentProperties.getCurrency())
                .build();
        paymentRepository.save(payment);

        var gatewayResponse = paymentGatewayClient.createPayment(new PaymentGatewayCreateRequest(
                order.getId(),
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency()
        ));

        payment.setStatus(gatewayResponse.status());
        payment.setProvider(gatewayResponse.provider());
        payment.setExternalId(gatewayResponse.externalPaymentId());
        payment.setPaymentUrl(gatewayResponse.paymentUrl());
        payment.setClientToken(gatewayResponse.clientToken());

        paymentRepository.save(payment);

        recordEvent(payment, null, payment.getStatus(), "SYSTEM", "Payment created");

        return paymentMapper.toResponse(payment);
    }


    @Transactional
    public void handleCallback(PaymentCallbackRequest callback, String rawPayload, String signature) {
        if (!paymentCallbackVerifier.isValid(rawPayload, signature)) {
            throw new ForbiddenOperationException("Invalid payment callback signature");
        }

        if (isDuplicateCallback(callback.externalEventId())) {
            log.info("Duplicate payment callback ignored: {}", callback.externalEventId());
            return;
        }

        Payment payment = findPaymentByExternalId(callback.externalPaymentId());
        PaymentStatus newStatus = callback.status();

        if (payment.getStatus() == newStatus) {
            recordEvent(payment, callback.externalEventId(), newStatus, "PAYMENT_CALLBACK", callback.details());
            log.info("Duplicate payment status ignored for payment {}", callback.externalPaymentId());
            return;
        }

        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        recordEvent(payment, callback.externalEventId(), newStatus, "PAYMENT_CALLBACK", callback.details());

        if (newStatus == PaymentStatus.SUCCEEDED) {
            orderService.markPaidFromPayment(payment.getOrder().getId());
            log.info("Order {} marked as PAID", payment.getOrder().getId());
        }
    }

    public void handleCallback(PaymentCallbackRequest callback, String signature) {
        handleCallback(callback, "", signature);
    }

    private void recordEvent(Payment payment, String externalEventId, PaymentStatus status, String source, String details) {
        PaymentEvent event = PaymentEvent.builder()
                .payment(payment)
                .externalEventId(externalEventId)
                .newStatus(status)
                .source(source)
                .details(details)
                .build();
        paymentEventRepository.save(event);
    }


    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForCurrentUser(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        accessControlService.requireOrderOwnerOrAdmin(payment.getOrder());
        return paymentMapper.toResponse(payment);
    }

    private void validateOrderCanBePaid(Order order) {
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BusinessRuleException("Order is already paid");
        }
        if (order.getStatus() != OrderStatus.NEW) {
            throw new BusinessRuleException("Only NEW orders can be paid");
        }
        if (shipmentRepository.findByOrder(order).isEmpty()) {
            throw new BusinessRuleException("Shipping must be selected before payment");
        }
        if (order.getPaymentAmount() == null || order.getPaymentAmount().signum() < 0) {
            throw new BusinessRuleException("Order payment amount is invalid");
        }
    }

    private void validatePaymentAttemptAllowed(Order order) {
        Optional<Payment> latestPayment = paymentRepository.findTopByOrderOrderByCreatedAtDesc(order);
        if (latestPayment.isEmpty()) {
            return;
        }

        PaymentStatus status = latestPayment.get().getStatus();
        if (status == PaymentStatus.CREATED || status == PaymentStatus.PENDING) {
            throw new BusinessRuleException("Payment is already in progress");
        }
        if (status == PaymentStatus.SUCCEEDED) {
            throw new BusinessRuleException("Order is already paid");
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private Payment findPaymentByExternalId(String externalPaymentId) {
        return paymentRepository.findByExternalId(externalPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for callback: " + externalPaymentId));
    }

    private boolean isDuplicateCallback(String externalEventId) {
        if (externalEventId == null || externalEventId.isBlank()) {
            return false;
        }
        return paymentEventRepository.findByExternalEventId(externalEventId).isPresent();
    }
}
