package com.example.shopupu.payments.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.shopupu.common.exception.*;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.payments.dto.PaymentCallbackRequest;
import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.gateway.*;
import com.example.shopupu.payments.mapper.PaymentMapperImpl;
import com.example.shopupu.payments.repository.*;
import com.example.shopupu.shipping.entity.*;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentEventRepository paymentEventRepository;
    @Mock PaymentRefundAttemptRepository refundAttemptRepository;
    @Mock OrderRepository orderRepository;
    @Mock PaymentGatewayClient gateway;
    @Mock PaymentCallbackVerifier verifier;
    @Mock ShipmentRepository shipments;
    @Mock OrderService orders;
    @Mock AccessControlService access;
    @Mock com.example.shopupu.common.audit.AuditService audit;
    private PaymentService service;
    private PaymentProperties properties;
    private Order order;
    private Payment payment;
    private Shipment shipment;

    @BeforeEach
    void setup() {
        properties = new PaymentProperties();
        properties.setDefaultProvider("stub");
        properties.setCurrency("EUR");
        var manager = mock(PlatformTransactionManager.class);
        lenient().when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new PaymentService(paymentRepository, paymentEventRepository, orderRepository, new PaymentMapperImpl(),
                gateway, verifier, properties, shipments, orders, access, new TransactionTemplate(manager), audit,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), refundAttemptRepository);
        order = Order.builder().id(1L).user(User.builder().id(1L).email("owner@example.com").build())
                .orderNumber("TEST").status(OrderStatus.CREATED).paymentAmount(new BigDecimal("24.99")).build();
        payment = Payment.builder().id(10L).order(order).provider("stub").status(PaymentStatus.PENDING)
                .externalId("ext-1").idempotencyKey("key").amount(order.getPaymentAmount()).currency("EUR").build();
        shipment = Shipment.builder().order(order).method(ShippingMethod.LOCAL_PICKUP).currency("EUR").build();
        lenient().when(paymentRepository.isOrderOwnerActive(1L)).thenReturn(true);
        lenient().when(orderRepository.findLockedById(1L)).thenReturn(Optional.of(order));
        lenient().when(paymentRepository.findOrderIdByPaymentId(10L)).thenReturn(Optional.of(1L));
        lenient().when(paymentRepository.findLockedById(10L)).thenAnswer(call -> Optional.of(payment));
        lenient().when(paymentRepository.findById(10L)).thenAnswer(call -> Optional.of(payment));
        lenient().when(paymentRepository.findIdByExternalId("ext-1")).thenReturn(Optional.of(10L));
        lenient().when(shipments.findByOrder(order)).thenReturn(Optional.of(shipment));
        lenient().when(paymentRepository.save(any())).thenAnswer(call -> {
            payment = call.getArgument(0);
            if (payment.getId() == null) payment.setId(10L);
            return payment;
        });
        lenient().when(paymentRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(paymentEventRepository.insertCallbackEvent(anyLong(), nullable(String.class), anyString(), anyString(), nullable(String.class)))
                .thenReturn(1);
        lenient().when(verifier.isValid("payload", "valid")).thenReturn(true);
        lenient().when(access.currentUser()).thenReturn(order.getUser());
    }

    @Test
    void erasedOwnerCannotCreateReplayOrSimulateButProviderCallbackCanSettle() {
        when(paymentRepository.isOrderOwnerActive(1L)).thenReturn(false);
        assertThrows(ForbiddenOperationException.class, () -> service.createPayment(1L, "new"));
        when(paymentRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(payment));
        assertThrows(ForbiddenOperationException.class, () -> service.createPayment(1L, "key"));
        assertThrows(ForbiddenOperationException.class, () -> service.simulateSuccess(10L));
        verifyNoInteractions(gateway);
        service.handleCallback(callback(PaymentStatus.SUCCEEDED), "payload", "valid");
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void createPaymentCallsGatewayAndMarksOrderPendingPayment() {
        when(gateway.createPayment(any())).thenReturn(new PaymentGatewayCreateResponse("ext-1", "stub", PaymentStatus.PENDING, "/demo", "token"));
        var response = service.createPayment(1L);
        assertEquals("ext-1", response.externalPaymentId());
        assertEquals(PaymentStatus.PENDING, response.status());
        verify(access).requireOrderOwnerOrAdmin(order);
        verify(orders).markPendingPayment(1L);
    }

    @Test
    void createPaymentRetainsUnknownOutcomeWhenGatewayThrows() {
        when(gateway.createPayment(any())).thenThrow(new IllegalStateException("timeout"));
        assertThrows(ServiceUnavailableException.class, () -> service.createPayment(1L));
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        verify(orders).markPendingPayment(1L);
    }

    @Test
    void createPaymentRejectsMissingOrderShippingPaidOrderAndPendingAttempt() {
        assertThrows(ResourceNotFoundException.class, () -> service.createPayment(404L));
        when(shipments.findByOrder(order)).thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class, () -> service.createPayment(1L));
        order.setStatus(OrderStatus.PAID);
        assertThrows(BusinessRuleException.class, () -> service.createPayment(1L));
        order.setStatus(OrderStatus.CREATED);
        when(shipments.findByOrder(order)).thenReturn(Optional.of(shipment));
        when(orderRepository.hasUnsettledPayment(1L)).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> service.createPayment(1L));
        verifyNoInteractions(gateway);
    }

    @Test
    void createPaymentRejectsInvalidDeliveryAmountAndCurrency() {
        shipment.setMethod(null);
        assertThrows(BusinessRuleException.class, () -> service.createPayment(1L));
        shipment.setMethod(ShippingMethod.DHL);
        assertThrows(BusinessRuleException.class, () -> service.createPayment(1L));
        shipment.setAddress(ShippingAddress.builder().fullName("Owner").line1("Main 1").city("Berlin")
                .state("Berlin").postalCode("10115").country("DE").build());
        shipment.setCurrency("USD");
        assertThrows(BusinessRuleException.class, () -> service.createPayment(1L));
        shipment.setCurrency("EUR");
        order.setPaymentAmount(BigDecimal.ZERO);
        assertThrows(BusinessRuleException.class, () -> service.createPayment(1L));
        verifyNoInteractions(gateway);
    }

    @Test
    void replayAuthorizesOriginalOrderAndRejectsRebinding() {
        when(paymentRepository.findByIdempotencyKey("key")).thenReturn(Optional.of(payment));
        assertEquals(10L, service.createPayment(1L, "key").id());
        assertThrows(ConflictException.class, () -> service.createPayment(2L, "key"));
        doThrow(new ForbiddenOperationException("denied")).when(access).requireOrderOwnerOrAdmin(order);
        assertThrows(ForbiddenOperationException.class, () -> service.createPayment(99L, "key"));
        verifyNoInteractions(gateway);
    }

    @Test
    void handleCallbackRejectsInvalidSignatureBeforeLookingUpPayment() {
        assertThrows(ForbiddenOperationException.class, () -> service.handleCallback(callback(PaymentStatus.SUCCEEDED), "payload", "bad"));
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void handleCallbackDeduplicatesExternalEventId() {
        when(paymentEventRepository.insertCallbackEvent(eq(10L), eq("event"), anyString(), anyString(), anyString())).thenReturn(0);
        service.handleCallback(callback(PaymentStatus.SUCCEEDED), "payload", "valid");
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verifyNoInteractions(orders);
    }

    @Test
    void handleCallbackUpdatesPaymentAndMarksOrderPaid() {
        service.handleCallback(callback(PaymentStatus.SUCCEEDED), "payload", "valid");
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        verify(orders).markPaidFromPayment(1L);
    }

    @Test
    void handleCallbackMarksOrderRetryableOnFailure() {
        service.handleCallback(callback(PaymentStatus.FAILED), "payload", "valid");
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(orders).onPaymentFailed(1L);
    }

    @Test
    void handleCallbackIgnoresIllegalOrDuplicateTransition() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        service.handleCallback(callback(PaymentStatus.FAILED), "payload", "valid");
        service.handleCallback(callback(PaymentStatus.SUCCEEDED), "payload", "valid");
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        verifyNoInteractions(orders);
    }

    @Test
    void callbackLocalIdCannotReplaceAnAlreadyBoundProviderIdentity() {
        var callback = new PaymentCallbackRequest("event", "foreign", PaymentStatus.SUCCEEDED, "test", 10L);
        assertThrows(BusinessRuleException.class, () -> service.handleCallback(callback, "payload", "valid"));
        verifyNoInteractions(orders);
    }

    @Test
    void refundPaymentOnlyRestocksAfterConfirmedOutcome() {
        order.setStatus(OrderStatus.PAID);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(access.currentEmail()).thenReturn("admin@example.com");
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class)))
                .thenReturn(new PaymentGatewayRefundResponse("refund-1", PaymentGatewayRefundStatus.PENDING));
        var response = service.refundPayment(10L);
        assertEquals(PaymentStatus.SUCCEEDED, response.status());
        assertEquals(PaymentGatewayRefundStatus.PENDING, response.refundStatus());
        assertNotNull(payment.getRefundOperationKey());
        verify(orders, never()).markRefunded(anyLong(), anyString());
        verify(access).requireAdmin();
    }

    @Test
    void refundPaymentRejectsNonSucceededOrIneligibleOrderBeforeProviderCall() {
        when(access.currentEmail()).thenReturn("admin@example.com");
        assertThrows(BusinessRuleException.class, () -> service.refundPayment(10L));
        payment.setStatus(PaymentStatus.SUCCEEDED);
        order.setStatus(OrderStatus.CANCELLED);
        assertThrows(BusinessRuleException.class, () -> service.refundPayment(10L));
        verifyNoInteractions(gateway);
    }

    @Test
    void getPaymentForCurrentUserChecksAccess() {
        assertEquals(10L, service.getPaymentForCurrentUser(10L).id());
        verify(access).requireOrderOwnerOrAdmin(order);
    }

    @Test
    void localSimulationOnlyAcceptsOwnerAndStubProvider() {
        when(access.currentUser()).thenReturn(User.builder().id(2L).build());
        assertThrows(ForbiddenOperationException.class, () -> service.simulateSuccess(10L));
        when(access.currentUser()).thenReturn(order.getUser());
        payment.setProvider("stripe");
        assertThrows(BusinessRuleException.class, () -> service.simulateSuccess(10L));
        payment.setProvider("stub");
        assertEquals(PaymentStatus.SUCCEEDED, service.simulateSuccess(10L).status());
        verify(orders).markPaidFromPayment(1L);
    }


    @Test
    void confirmedRefundKeepsLongActorEmailOutOfBoundedEventSource() {
        String actor = "long-admin-address-for-audited-demo-refunds@example.com";
        order.setStatus(OrderStatus.PAID);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(access.currentEmail()).thenReturn(actor);
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class)))
                .thenReturn(new PaymentGatewayRefundResponse("refund-1", PaymentGatewayRefundStatus.SUCCEEDED));
        assertEquals(PaymentStatus.REFUNDED, service.refundPayment(10L).status());
        verify(paymentEventRepository).insertCallbackEvent(eq(10L), isNull(), eq("REFUNDED"), eq("REFUND"), anyString());
        verify(orders).markRefunded(1L, actor);
    }


    @Test
    void verifiedCallbackRejectsWrongProviderBeforeBindingOrDedupe() {
        payment.setExternalId(null);
        var callback = new PaymentCallbackRequest("event", "ext-foreign", PaymentStatus.SUCCEEDED, "test", 10L);
        assertThrows(ForbiddenOperationException.class, () -> service.applyVerifiedCallback("stripe", callback));
        assertNull(payment.getExternalId());
        verifyNoInteractions(paymentEventRepository, orders);
    }

    @Test
    void legacySignedCallbackCannotUpdateAnotherProvidersPayment() {
        payment.setProvider("stripe");
        assertThrows(ForbiddenOperationException.class, () -> service.handleCallback(callback(PaymentStatus.SUCCEEDED), "payload", "valid"));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verifyNoInteractions(paymentEventRepository, orders);
    }


    @Test
    void refundReconciliationWrapsCursorWithoutExceedingOneProviderBatch() {
        payment.setRefundOperationKey("refund-key");
        payment.setRefundStatus(PaymentGatewayRefundStatus.UNKNOWN);
        var statuses = java.util.EnumSet.of(PaymentGatewayRefundStatus.PENDING, PaymentGatewayRefundStatus.UNKNOWN);
        var page = org.springframework.data.domain.PageRequest.of(0, 100);
        when(paymentRepository.findRefundCandidates("stub", statuses, 0L, page)).thenReturn(java.util.List.of(payment));
        when(paymentRepository.findRefundCandidates("stub", statuses, 10L, page)).thenReturn(java.util.List.of());
        when(gateway.fetchRefundStatus(any(), nullable(String.class))).thenReturn(Optional.empty());
        service.reconcileRefunds();
        service.reconcileRefunds();
        verify(paymentRepository, times(2)).findRefundCandidates("stub", statuses, 0L, page);
        verify(paymentRepository).findRefundCandidates("stub", statuses, 10L, page);
        verify(gateway, times(2)).fetchRefundStatus(any(), nullable(String.class));
    }


    @Test
    void unavailableProviderFailsBeforePersistingAttemptOrStartingPayment() {
        doThrow(new BusinessRuleException("Provider is not configured")).when(gateway).ensureAvailable();
        assertThrows(BusinessRuleException.class, () -> service.createPayment(1L));
        verify(paymentRepository, never()).save(any());
        verify(gateway, never()).createPayment(any());
        verifyNoInteractions(orders);
    }


    @Test
    void stripeRejectsLegacyCustomHmacCallbackEvenIfVerifierWouldAcceptIt() {
        properties.setDefaultProvider("stripe");
        assertThrows(ForbiddenOperationException.class, () -> service.handleCallback(callback(PaymentStatus.SUCCEEDED), "payload", "valid"));
        verifyNoInteractions(verifier, paymentRepository, paymentEventRepository, orders);
    }


    @Test
    void createRecoveryAdvancesPastUnrecoverableRowsAndWrapsInBoundedBatches() {
        payment.setStatus(PaymentStatus.CREATED);
        payment.setExternalId(null);
        payment.setCreatedAt(java.time.Instant.now().minusSeconds(120));
        payment.setProviderRequestContext("original-body");
        var page = org.springframework.data.domain.PageRequest.of(0, 100);
        when(paymentRepository.findCreateRecoveryCandidates(eq("stub"), any(), eq(0L), eq(page)))
                .thenReturn(java.util.List.of(payment));
        when(paymentRepository.findCreateRecoveryCandidates(eq("stub"), any(), eq(10L), eq(page)))
                .thenReturn(java.util.List.of());
        when(gateway.recoverPayment(any(), any(), any())).thenReturn(Optional.empty());
        service.reconcileCreates();
        service.reconcileCreates();
        verify(paymentRepository, times(2)).findCreateRecoveryCandidates(eq("stub"), any(), eq(0L), eq(page));
        verify(paymentRepository).findCreateRecoveryCandidates(eq("stub"), any(), eq(10L), eq(page));
        verify(gateway, times(2)).recoverPayment(any(), any(), any());
    }

    private PaymentCallbackRequest callback(PaymentStatus status) {
        return new PaymentCallbackRequest("event", "ext-1", status, "test");
    }
}
