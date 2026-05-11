package com.example.shopupu.payments.service;

import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.payments.dto.PaymentCallbackRequest;
import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentEvent;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.gateway.PaymentCallbackVerifier;
import com.example.shopupu.payments.gateway.PaymentGatewayClient;
import com.example.shopupu.payments.gateway.PaymentGatewayCreateRequest;
import com.example.shopupu.payments.gateway.PaymentGatewayCreateResponse;
import com.example.shopupu.payments.mapper.PaymentMapper;
import com.example.shopupu.payments.repository.PaymentEventRepository;
import com.example.shopupu.payments.repository.PaymentRepository;
import com.example.shopupu.shipping.entity.Shipment;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * describes the PaymentServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentCallbackVerifier paymentCallbackVerifier;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private AccessControlService accessControlService;

    private PaymentService paymentService;
    private Order order;

    // handles setUp.
    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties();
        properties.setDefaultProvider("stub");
        properties.setCurrency("EUR");
        paymentService = new PaymentService(
                paymentRepository,
                paymentEventRepository,
                orderRepository,
                new PaymentMapper(),
                paymentGatewayClient,
                paymentCallbackVerifier,
                properties,
                shipmentRepository,
                orderService,
                accessControlService
        );
        order = order(1L, OrderStatus.NEW, new BigDecimal("24.99"));
    }

    // handles createPayment.
    @Test
    void createPaymentCreatesLocalPaymentAndGatewayPayment() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.of(new Shipment()));
        when(paymentRepository.findTopByOrderOrderByCreatedAtDesc(order)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(10L);
            }
            return payment;
        });
        when(paymentGatewayClient.createPayment(any(PaymentGatewayCreateRequest.class)))
                .thenReturn(new PaymentGatewayCreateResponse("ext-1", "stub", PaymentStatus.PENDING, "/pay/ext-1", "token"));

        var response = paymentService.createPayment(1L);

        assertEquals(10L, response.id());
        assertEquals("ext-1", response.externalPaymentId());
        assertEquals(PaymentStatus.PENDING, response.status());
        verify(accessControlService).requireOrderOwnerOrAdmin(order);
        verify(paymentEventRepository).save(any(PaymentEvent.class));
    }

    // handles createPayment.
    @Test
    void createPaymentRejectsMissingOrderMissingShippingPaidOrderAndPendingAttempt() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> paymentService.createPayment(404L));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(shipmentRepository.findByOrder(order)).thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class, () -> paymentService.createPayment(1L));

        Order paid = order(2L, OrderStatus.PAID, new BigDecimal("10.00"));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(paid));
        assertThrows(BusinessRuleException.class, () -> paymentService.createPayment(2L));

        Order withPendingPayment = order(3L, OrderStatus.NEW, new BigDecimal("10.00"));
        Payment pending = payment(withPendingPayment, PaymentStatus.PENDING, "ext-pending");
        when(orderRepository.findById(3L)).thenReturn(Optional.of(withPendingPayment));
        when(shipmentRepository.findByOrder(withPendingPayment)).thenReturn(Optional.of(new Shipment()));
        when(paymentRepository.findTopByOrderOrderByCreatedAtDesc(withPendingPayment)).thenReturn(Optional.of(pending));
        assertThrows(BusinessRuleException.class, () -> paymentService.createPayment(3L));
    }

    // handles handleCallback.
    @Test
    void handleCallbackRejectsInvalidSignature() {
        PaymentCallbackRequest request = new PaymentCallbackRequest("event-1", "ext-1", PaymentStatus.SUCCEEDED, "ok");
        when(paymentCallbackVerifier.isValid("payload", "bad")).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () -> paymentService.handleCallback(request, "payload", "bad"));
    }

    // handles handleCallback.
    @Test
    void handleCallbackIgnoresDuplicateExternalEventId() {
        PaymentCallbackRequest request = new PaymentCallbackRequest("event-1", "ext-1", PaymentStatus.SUCCEEDED, "ok");
        when(paymentCallbackVerifier.isValid("payload", "secret")).thenReturn(true);
        when(paymentEventRepository.findByExternalEventId("event-1")).thenReturn(Optional.of(new PaymentEvent()));

        paymentService.handleCallback(request, "payload", "secret");

        verify(paymentRepository, never()).findByExternalId("ext-1");
    }

    // handles handleCallback.
    @Test
    void handleCallbackUpdatesPaymentAndMarksOrderPaidOnSuccess() {
        Payment payment = payment(order, PaymentStatus.PENDING, "ext-1");
        PaymentCallbackRequest request = new PaymentCallbackRequest("event-1", "ext-1", PaymentStatus.SUCCEEDED, "ok");
        when(paymentCallbackVerifier.isValid("payload", "secret")).thenReturn(true);
        when(paymentEventRepository.findByExternalEventId("event-1")).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalId("ext-1")).thenReturn(Optional.of(payment));

        paymentService.handleCallback(request, "payload", "secret");

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        verify(paymentRepository).save(payment);
        verify(paymentEventRepository).save(any(PaymentEvent.class));
        verify(orderService).markPaidFromPayment(1L);
    }

    // handles handleCallback.
    @Test
    void handleCallbackRecordsDuplicateStatusWithoutSavingPayment() {
        Payment payment = payment(order, PaymentStatus.PENDING, "ext-1");
        PaymentCallbackRequest request = new PaymentCallbackRequest("event-1", "ext-1", PaymentStatus.PENDING, "ok");
        when(paymentCallbackVerifier.isValid("payload", "secret")).thenReturn(true);
        when(paymentEventRepository.findByExternalEventId("event-1")).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalId("ext-1")).thenReturn(Optional.of(payment));

        paymentService.handleCallback(request, "payload", "secret");

        verify(paymentRepository, never()).save(payment);
        verify(paymentEventRepository).save(any(PaymentEvent.class));
    }

    // handles getPaymentForCurrentUser.
    @Test
    void getPaymentForCurrentUserChecksAccessAndMapsPayment() {
        Payment payment = payment(order, PaymentStatus.PENDING, "ext-1");
        payment.setId(10L);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        var response = paymentService.getPaymentForCurrentUser(10L);

        assertEquals(10L, response.id());
        verify(accessControlService).requireOrderOwnerOrAdmin(order);
    }

    private Order order(Long id, OrderStatus status, BigDecimal paymentAmount) {
        Order order = new Order();
        order.setId(id);
        order.setUser(User.builder().id(1L).email("user@example.com").build());
        order.setStatus(status);
        order.setPaymentAmount(paymentAmount);
        return order;
    }

    private Payment payment(Order order, PaymentStatus status, String externalId) {
        return Payment.builder()
                .id(10L)
                .order(order)
                .amount(order.getPaymentAmount())
                .currency("EUR")
                .provider("stub")
                .status(status)
                .externalId(externalId)
                .idempotencyKey("key")
                .build();
    }
}
