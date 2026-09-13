package com.example.shopupu.payments.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.shopupu.cart.entity.*;
import com.example.shopupu.cart.repository.*;
import com.example.shopupu.catalog.entity.*;
import com.example.shopupu.catalog.repository.*;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ServiceUnavailableException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.inventory.repository.InventoryRepository;
import com.example.shopupu.inventory.service.InventoryService;
import com.example.shopupu.orders.entity.*;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.payments.dto.*;
import com.example.shopupu.payments.entity.*;
import com.example.shopupu.payments.gateway.*;
import com.example.shopupu.payments.repository.*;
import com.example.shopupu.shipping.entity.*;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
class PaymentIntegrityIT extends PostgresContainerSupport {
    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentEventRepository eventRepository;
    @Autowired OrderService orders;
    @Autowired com.example.shopupu.orders.controller.OrderController orderController;
    @Autowired OrderRepository orderRepository;
    @Autowired InventoryService inventory;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired UserRepository users;
    @Autowired com.example.shopupu.identity.repository.RoleRepository roles;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired ShipmentRepository shipments;
    @Autowired com.example.shopupu.shipping.service.ShippingService shipping;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean PaymentGatewayClient gateway;
    @MockitoBean PaymentCallbackVerifier verifier;
    private User owner;
    private ProductVariant variant;

    @BeforeEach
    void setup() {
        owner = user();
        login(owner);
        Category category = categories.save(new Category("Tee", key(), null, null));
        Product product = new Product("Tee", key(), "demo", new BigDecimal("20.00"), category);
        product.setEnabled(true);
        product = products.save(product);
        variant = variants.save(ProductVariant.builder().product(product).sku(key()).size("M")
                .color("black").price(new BigDecimal("20.00")).enabled(true).build());
        inventory.setStock(variant.getId(), 98, "test");
        // The container is shared by every IT in this JVM and earlier tests leave unfinished stub payments and
        // unresolved refunds behind. Expiry/reconciliation batches are bounded, so settle those leftovers first.
        jdbc.update("update payments set status = 'FAILED' where provider = 'stub' and status in ('CREATED', 'PENDING')");
        jdbc.update("update payments set refund_status = 'FAILED' where refund_status in ('PENDING', 'UNKNOWN')");
        when(verifier.isValid(anyString(), anyString())).thenReturn(true);
        lenient().when(gateway.createPayment(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new PaymentGatewayCreateResponse(key(), "stub", PaymentStatus.PENDING, "/demo", "test-token");
        });
    }

    @AfterEach
    void clearAuth() { SecurityContextHolder.clearContext(); }

    @Test
    void erasedAccountCannotStartOrSimulatePaymentButItsPendingProviderResultStillApplies() {
        Order pendingOrder = order(owner, 1);
        Order newOrder = order(owner, 1);
        var existing = payments.createPayment(pendingOrder.getId(), key());
        jdbc.update("update users set enabled=false, deleted_at=now() where id=?", owner.getId());
        assertThrows(ForbiddenOperationException.class, () -> payments.createPayment(newOrder.getId(), key()));
        assertThrows(ForbiddenOperationException.class, () -> payments.simulateSuccess(existing.id()));
        payments.applyVerifiedCallback("stub", new PaymentCallbackRequest(key(), existing.externalPaymentId(),
                PaymentStatus.SUCCEEDED, "verified provider result"));
        assertEquals(OrderStatus.PAID, orderRepository.findById(pendingOrder.getId()).orElseThrow().getStatus());
    }

    @Test
    void verifiedOpenStripeRecoveryRestoresCheckoutUrlButNeverRestoresErasedOwnersAccessFields() {
        Order order = order(owner, 1);
        Payment prepared = paymentRepository.save(Payment.builder().order(order).provider("stripe")
                .status(PaymentStatus.CREATED).amount(new BigDecimal("20.00")).currency("EUR")
                .idempotencyKey(key()).build());
        login(admin());
        var response = new PaymentGatewayCreateResponse("cs_test_recovery", "stripe", PaymentStatus.PENDING,
                "https://checkout.stripe.com/c/pay/cs_test_recovery", null);
        var recovered = payments.applyVerifiedSessionRecovery(prepared.getId(), response);
        assertEquals(PaymentStatus.PENDING, recovered.status());
        assertEquals(response.paymentUrl(), recovered.paymentUrl());
        assertEquals(response.externalPaymentId(), recovered.externalPaymentId());
        jdbc.update("update users set enabled=false, deleted_at=now() where id=?", owner.getId());
        jdbc.update("update payments set payment_url=null where id=?", prepared.getId());
        var afterErasure = payments.applyVerifiedSessionRecovery(prepared.getId(), response);
        assertNull(afterErasure.paymentUrl());
        assertEquals(PaymentStatus.PENDING, afterErasure.status());
    }

    @Test
    void reconciliationQueryFiltersProviderBeforeLimitAndContinuesPastUnknownRows() {
        Order order = order(owner, 1);
        String prefix = key();
        jdbc.update("""
                insert into payments(order_id,provider,external_id,amount,currency,status,idempotency_key,created_at)
                select ?, 'stripe', ? || '-foreign-' || n, 20, 'EUR', 'PENDING', ? || '-foreign-' || n,
                    now() - interval '10 minutes' from generate_series(1,100) n
                """, order.getId(), prefix, prefix);
        jdbc.update("""
                insert into payments(order_id,provider,external_id,amount,currency,status,idempotency_key,created_at)
                select ?, 'stub', ? || '-local-' || n, 20, 'EUR', 'PENDING', ? || '-local-' || n,
                    now() - interval '10 minutes' from generate_series(1,101) n
                """, order.getId(), prefix, prefix);
        Long first = jdbc.queryForObject("select min(id) from payments where idempotency_key like ?", Long.class, prefix + "-%");
        var batch = paymentRepository.findReconciliationCandidates("stub", List.of(PaymentStatus.PENDING),
                Instant.now().minusSeconds(86400), Instant.now().minusSeconds(300), first - 1,
                org.springframework.data.domain.PageRequest.of(0, 100));
        assertEquals(100, batch.size());
        assertTrue(batch.stream().allMatch(candidate -> "stub".equals(candidate.provider())));
        var next = paymentRepository.findReconciliationCandidates("stub", List.of(PaymentStatus.PENDING),
                Instant.now().minusSeconds(86400), Instant.now().minusSeconds(300), batch.getLast().paymentId(),
                org.springframework.data.domain.PageRequest.of(0, 100));
        assertEquals(1, next.size());
    }

    @Test
    void replayCannotDiscloseAnotherOwnersPayment() {
        Order order = order(owner, 1);
        String idempotency = key();
        PaymentResponse created = payments.createPayment(order.getId(), idempotency);
        assertEquals(created.id(), payments.createPayment(order.getId(), idempotency).id());
        login(user());
        assertThrows(ForbiddenOperationException.class, () -> payments.createPayment(999999L, idempotency));
        verify(gateway, times(1)).createPayment(any());
    }

    @Test
    void sameKeyCannotBeReboundToAnotherOrder() {
        Order first = order(owner, 1);
        Order second = order(owner, 1);
        String idempotency = key();
        payments.createPayment(first.getId(), idempotency);
        assertThrows(com.example.shopupu.common.exception.ConflictException.class, () -> payments.createPayment(second.getId(), idempotency));
        verify(gateway, times(1)).createPayment(any());
    }

    @Test
    void concurrentSameKeyCreatesOneProviderAttempt() throws Exception {
        Order order = order(owner, 1);
        String idempotency = key();
        List<PaymentResponse> responses = concurrent(6, i -> {
            login(owner);
            try { return payments.createPayment(order.getId(), idempotency); }
            finally { SecurityContextHolder.clearContext(); }
        });
        assertEquals(1, responses.stream().map(PaymentResponse::id).distinct().count());
        verify(gateway, times(1)).createPayment(any());
    }

    @Test
    void concurrentSuccessesDoNotConsumeOtherOrdersReservation() throws Exception {
        Order order = order(owner, 1);
        order(user(), 5);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        concurrent(8, i -> {
            callback(payment.externalPaymentId(), "event-" + i + "-" + payment.id(), PaymentStatus.SUCCEEDED);
            return true;
        });
        assertStock(97, 5);
        assertEquals(OrderStatus.PAID, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(1, eventRepository.findByPayment(paymentRepository.findById(payment.id()).orElseThrow()).stream()
                .filter(e -> e.getNewStatus() == PaymentStatus.SUCCEEDED && !e.getDetails().startsWith("Duplicate")).count());
    }

    @Test
    void concurrentSameEventIsIdempotent() throws Exception {
        Order order = order(owner, 1);
        order(user(), 5);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        String event = key();
        concurrent(6, i -> { callback(payment.externalPaymentId(), event, PaymentStatus.SUCCEEDED); return true; });
        assertStock(97, 5);
        assertTrue(eventRepository.findByExternalEventId(event).isPresent());
    }

    @Test
    void concurrentRefundCallbacksRestoreStockOnceAndSyncOrder() throws Exception {
        Order order = order(owner, 1);
        order(user(), 5);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        concurrent(6, i -> { callback(payment.externalPaymentId(), key(), PaymentStatus.REFUNDED); return true; });
        assertStock(98, 5);
        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(payment.id()).orElseThrow().getStatus());
    }

    @Test
    void deliveryWithoutAddressAndZeroAmountCannotStartPayment() {
        Order order = order(owner, 1);
        Shipment shipment = shipments.findByOrder(order).orElseThrow();
        shipment.setMethod(ShippingMethod.DHL);
        shipments.save(shipment);
        assertThrows(BusinessRuleException.class, () -> payments.createPayment(order.getId(), key()));
        shipment.setMethod(ShippingMethod.LOCAL_PICKUP);
        shipments.save(shipment);
        order.setPaymentAmount(BigDecimal.ZERO);
        orderRepository.save(order);
        assertThrows(BusinessRuleException.class, () -> payments.createPayment(order.getId(), key()));
        verify(gateway, never()).createPayment(any());
    }

    @Test
    void unknownCreateOutcomeCannotBeRetriedOrExpiredLocally() {
        Order order = order(owner, 1);
        when(gateway.createPayment(any())).thenThrow(new IllegalStateException("read timed out"));
        String idempotency = key();
        assertThrows(ServiceUnavailableException.class, () -> payments.createPayment(order.getId(), idempotency));
        PaymentResponse pending = payments.createPayment(order.getId(), idempotency);
        assertEquals(PaymentStatus.CREATED, pending.status());
        assertThrows(BusinessRuleException.class, () -> payments.createPayment(order.getId(), key()));
        assertThrows(BusinessRuleException.class, () -> orders.cancelOrder(order.getId()));
        Payment stored = paymentRepository.findById(pending.id()).orElseThrow();
        stored.setProvider("stripe");
        paymentRepository.save(stored);
        payments.expireStalePayments(Instant.now().plusSeconds(3600));
        assertEquals(PaymentStatus.CREATED, paymentRepository.findById(pending.id()).orElseThrow().getStatus());
        assertStock(98, 1);
        verify(gateway, times(1)).createPayment(any());
    }

    @Test
    void fastCallbackBeforeCreateResponseIsNotLostOrRegressed() {
        Order order = order(owner, 1);
        String external = key();
        when(gateway.createPayment(any())).thenAnswer(call -> {
            PaymentGatewayCreateRequest request = call.getArgument(0);
            payments.handleCallback(new PaymentCallbackRequest(key(), external, PaymentStatus.SUCCEEDED, "test", request.paymentId()),
                    "payload", "valid");
            return new PaymentGatewayCreateResponse(external, "stub", PaymentStatus.PENDING, "/demo", "token");
        });
        PaymentResponse response = payments.createPayment(order.getId(), key());
        assertEquals(PaymentStatus.SUCCEEDED, response.status());
        assertEquals(OrderStatus.PAID, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertStock(97, 0);
    }


    @Test
    void refundHttpRunsOutsideTransactionAndOnlyOneConcurrentAttempt() throws Exception {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        User admin = admin();
        AtomicInteger calls = new AtomicInteger();
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            calls.incrementAndGet();
            return new PaymentGatewayRefundResponse(key(), PaymentGatewayRefundStatus.SUCCEEDED);
        });
        concurrent(5, i -> {
            login(admin);
            try { return payments.refundPayment(payment.id()); }
            finally { SecurityContextHolder.clearContext(); }
        });
        assertEquals(1, calls.get());
        assertStock(98, 0);
    }

    @Test
    void unknownRefundOutcomeRetainsOperationAndDoesNotRestockOrRetry() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class))).thenThrow(new IllegalStateException("read timed out"));
        assertThrows(ServiceUnavailableException.class, () -> payments.refundPayment(payment.id()));
        payments.refundPayment(payment.id());
        verify(gateway, times(1)).refundPayment(any(PaymentGatewayRefundRequest.class));
        assertStock(97, 0);
        assertEquals(PaymentStatus.SUCCEEDED, paymentRepository.findById(payment.id()).orElseThrow().getStatus());
    }


    @Test
    void providerPreparationFreezesShippingBeforeHttpCompletes() throws Exception {
        Order order = order(owner, 1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(gateway.createPayment(any())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(finish.await(10, TimeUnit.SECONDS));
            return new PaymentGatewayCreateResponse(key(), "stub", PaymentStatus.PENDING, "/demo", "token");
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> {
                login(owner);
                try { return payments.createPayment(order.getId(), key()); }
                finally { SecurityContextHolder.clearContext(); }
            });
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertThrows(BusinessRuleException.class, () -> shipping.setMethod(
                        new com.example.shopupu.shipping.dto.SetShippingMethodRequest(order.getId(), ShippingMethod.DHL)));
                assertThrows(BusinessRuleException.class, () -> orders.updateShippingAmount(order.getId(), new BigDecimal("9.99")));
            } finally { finish.countDown(); }
            assertEquals(new BigDecimal("20.00"), future.get(10, TimeUnit.SECONDS).amount());
        }
    }

    @Test
    void concurrentCancellationAndCreateCannotReleasePreparedPaymentsStock() throws Exception {
        Order order = order(owner, 1);
        order(user(), 5);
        concurrent(2, i -> {
            login(owner);
            try {
                if (i == 0) payments.createPayment(order.getId(), key());
                else orders.cancelOrder(order.getId());
            } catch (BusinessRuleException expected) {
                // Exactly one path can own the order transition.
            } finally { SecurityContextHolder.clearContext(); }
            return true;
        });
        OrderStatus status = orderRepository.findById(order.getId()).orElseThrow().getStatus();
        assertTrue(status == OrderStatus.CANCELLED || status == OrderStatus.PENDING_PAYMENT);
        assertStock(98, status == OrderStatus.CANCELLED ? 5 : 6);
        verify(gateway, times(status == OrderStatus.CANCELLED ? 0 : 1)).createPayment(any());
    }

    @Test
    void concurrentOrderTtlAndSuccessCannotReleaseOrSellTwice() throws Exception {
        Order order = order(owner, 1);
        order(user(), 5);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        jdbc.update("update orders set created_at = now() - interval '2 days' where id = ?", order.getId());
        concurrent(2, i -> {
            if (i == 0) payments.expireStalePayments(Instant.EPOCH);
            else callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
            orders.expireStaleOrders();
            return true;
        });
        assertEquals(OrderStatus.PAID, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertStock(97, 5);
    }

    @Test
    void stubExpiryAllowsTtlToReleaseOnlyItsOwnReservation() {
        Order order = order(owner, 1);
        order(user(), 5);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        jdbc.update("update orders set created_at = now() - interval '2 days' where id = ?", order.getId());
        assertEquals(0, orders.expireStaleOrders());
        payments.expireStalePayments(Instant.now().plusSeconds(1));
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(payment.id()).orElseThrow().getStatus());
        assertEquals(1, orders.expireStaleOrders());
        assertStock(98, 5);
    }

    @Test
    void refundReconciliationConfirmsPersistedOperationWithoutAnotherPost() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class)))
                .thenReturn(new PaymentGatewayRefundResponse("refund-" + payment.id(), PaymentGatewayRefundStatus.PENDING));
        payments.refundPayment(payment.id());
        Payment before = paymentRepository.findById(payment.id()).orElseThrow();
        when(gateway.fetchRefundStatus(any(), anyString())).thenAnswer(call -> {
            PaymentGatewayRefundRequest request = call.getArgument(0);
            // Reconciliation walks every unresolved candidate; only this test's payment is under assertion.
            if (!payment.id().equals(request.paymentId())) return Optional.empty();
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(before.getRefundOperationKey(), request.idempotencyKey());
            assertEquals(payment.amount(), request.amount());
            return Optional.of(new PaymentGatewayRefundResponse("refund-" + payment.id(), PaymentGatewayRefundStatus.SUCCEEDED));
        });
        payments.reconcileRefunds();
        Payment after = paymentRepository.findById(payment.id()).orElseThrow();
        assertEquals(before.getRefundOperationKey(), after.getRefundOperationKey());
        assertEquals(PaymentStatus.REFUNDED, after.getStatus());
        assertStock(98, 0);
        verify(gateway, times(1)).refundPayment(any(PaymentGatewayRefundRequest.class));
    }


    @Test
    void refundConfirmationBeforeSuccessCallbackStillSettlesExactlyOnce() {
        Order order = order(owner, 1);
        order(user(), 5);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.REFUNDED);
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(payment.id()).orElseThrow().getStatus());
        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertStock(98, 5);
    }

    @Test
    void shippedOrderCanReceiveConfirmedFullRefundOnce() throws Exception {
        Order order = order(owner, 1);
        order(user(), 5);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        orders.updateStatus(order.getId(), OrderStatus.SHIPPED);
        concurrent(3, i -> { callback(payment.externalPaymentId(), key(), PaymentStatus.REFUNDED); return true; });
        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertStock(98, 5);
    }


    @Test
    void checkoutReplayRejectsChangedPromoBeforeLookingAtTheEmptiedCart() {
        Order order = order(owner, 1);
        assertEquals(order.getId(), orders.createOrderFromCart(owner, order.getIdempotencyKey(), null).getId());
        assertThrows(com.example.shopupu.common.exception.ConflictException.class,
                () -> orders.createOrderFromCart(owner, order.getIdempotencyKey(), "DIFFERENT"));
        assertStock(98, 1);
    }

    @Test
    void concurrentCheckoutSameUserAndKeyCreatesOneOrderAndReservation() throws Exception {
        Cart cart = carts.save(Cart.builder().user(owner).build());
        cartItems.save(CartItem.builder().cart(cart).variant(variant).quantity(1).build());
        String idempotency = key();
        var responses = concurrent(6, i -> orders.createOrderFromCart(owner, idempotency));
        assertEquals(1, responses.stream().map(Order::getId).distinct().count());
        assertStock(98, 1);
    }

    @Test
    void checkoutKeyIsScopedToUser() throws Exception {
        User other = user();
        for (User user : List.of(owner, other)) {
            Cart cart = carts.save(Cart.builder().user(user).build());
            cartItems.save(CartItem.builder().cart(cart).variant(variant).quantity(1).build());
        }
        String idempotency = key();
        var responses = concurrent(2, i -> orders.createOrderFromCart(i == 0 ? owner : other, idempotency));
        assertEquals(2, responses.stream().map(Order::getId).distinct().count());
        assertStock(98, 2);
    }


    @Test
    void checkoutControllerCanReplayAfterCartIsEmptyWithOsivDisabled() {
        Order order = order(owner, 1);
        var response = orderController.createOrder(order.getIdempotencyKey(), null).getBody();
        assertNotNull(response);
        assertEquals(order.getId(), response.id());
        assertEquals(1, response.items().size());
    }

    @Test
    void adminStatusResponseMapsLazyItemsBeforeTransactionCloses() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        var response = orders.updateStatusResponse(order.getId(), OrderStatus.PROCESSING);
        assertEquals(OrderStatus.PROCESSING, response.status());
        assertEquals(1, response.items().size());
    }


    @Test
    void callbacksCannotCrossProviderBoundaryByEitherLookupPath() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        Payment stored = paymentRepository.findById(payment.id()).orElseThrow();
        stored.setProvider("stripe");
        paymentRepository.save(stored);
        String externalEvent = key();
        assertThrows(ForbiddenOperationException.class,
                () -> callback(payment.externalPaymentId(), externalEvent, PaymentStatus.SUCCEEDED));
        String localEvent = key();
        assertThrows(ForbiddenOperationException.class, () -> payments.applyVerifiedCallback("stub",
                new PaymentCallbackRequest(localEvent, "foreign-id", PaymentStatus.SUCCEEDED, "test", payment.id())));
        assertTrue(eventRepository.findByExternalEventId(externalEvent).isEmpty());
        assertTrue(eventRepository.findByExternalEventId(localEvent).isEmpty());
        assertEquals(payment.externalPaymentId(), paymentRepository.findById(payment.id()).orElseThrow().getExternalId());
        assertStock(98, 1);
        payments.applyVerifiedCallback("stripe", new PaymentCallbackRequest(key(), payment.externalPaymentId(),
                PaymentStatus.SUCCEEDED, "test", payment.id()));
        assertStock(97, 0);
    }

    @Test
    void expiryReachesEligibleCandidatesBehindOneHundredUnknownProviderPayments() {
        String prefix = key().substring(0, 8);
        List<Order> blocked = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            blocked.add(Order.builder().user(owner).orderNumber(prefix + "-" + i)
                    .status(OrderStatus.PENDING_PAYMENT).paymentAmount(new BigDecimal("20.00")).build());
        }
        blocked = orderRepository.saveAll(blocked);
        List<Payment> unresolved = new ArrayList<>();
        for (Order blockedOrder : blocked) {
            unresolved.add(Payment.builder().order(blockedOrder).provider("stripe").status(PaymentStatus.CREATED)
                    .amount(new BigDecimal("20.00")).currency("EUR").idempotencyKey(key()).build());
        }
        paymentRepository.saveAll(unresolved);
        Order candidate = order(owner, 1);
        PaymentResponse candidatePayment = payments.createPayment(candidate.getId(), key());
        jdbc.update("update orders set created_at = now() - interval '2 days' where order_number like ? or id = ?", prefix + "-%", candidate.getId());
        assertTrue(paymentRepository.findStaleUnfinishedIds(Instant.now().plusSeconds(1)).contains(candidatePayment.id()));
        payments.expireStalePayments(Instant.now().plusSeconds(1));
        assertEquals(PaymentStatus.EXPIRED, paymentRepository.findById(candidatePayment.id()).orElseThrow().getStatus());
        assertTrue(orderRepository.findStaleUnpaidIds(Instant.now()).contains(candidate.getId()));
        orders.expireStaleOrders();
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(candidate.getId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.CREATED, paymentRepository.findById(unresolved.getFirst().getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, orderRepository.findById(blocked.getFirst().getId()).orElseThrow().getStatus());
        assertStock(98, 0);
    }

    @Test
    void realStubRecoversRefundInterruptedAfterPrepareBeforeResponseWithoutASecondPost() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        StubPaymentGatewayClient realStub = new StubPaymentGatewayClient();
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class))).thenAnswer(call -> {
            PaymentGatewayRefundRequest request = call.getArgument(0);
            assertNotNull(paymentRepository.findById(request.paymentId()).orElseThrow().getRefundOperationKey());
            realStub.refundPayment(request);
            throw new AssertionError("simulated process interruption after provider outcome before apply");
        });
        assertThrows(AssertionError.class, () -> payments.refundPayment(payment.id()));
        Payment interrupted = paymentRepository.findById(payment.id()).orElseThrow();
        assertEquals(PaymentGatewayRefundStatus.PENDING, interrupted.getRefundStatus());
        assertNull(interrupted.getRefundExternalId());
        when(gateway.fetchRefundStatus(any(), nullable(String.class))).thenAnswer(call ->
                new StubPaymentGatewayClient().fetchRefundStatus(call.getArgument(0), call.getArgument(1)));
        payments.reconcileRefunds();
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(payment.id()).orElseThrow().getStatus());
        assertStock(98, 0);
        verify(gateway, times(1)).refundPayment(any(PaymentGatewayRefundRequest.class));
    }

    @Test
    void realStubRecoversUnknownRefundEvenWhenNoProviderResponseIdWasStored() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class))).thenThrow(new IllegalStateException("unknown response"));
        assertThrows(ServiceUnavailableException.class, () -> payments.refundPayment(payment.id()));
        Payment interrupted = paymentRepository.findById(payment.id()).orElseThrow();
        assertEquals(PaymentGatewayRefundStatus.UNKNOWN, interrupted.getRefundStatus());
        assertNull(interrupted.getRefundExternalId());
        when(gateway.fetchRefundStatus(any(), nullable(String.class))).thenAnswer(call ->
                new StubPaymentGatewayClient().fetchRefundStatus(call.getArgument(0), call.getArgument(1)));
        payments.reconcileRefunds();
        Payment recovered = paymentRepository.findById(payment.id()).orElseThrow();
        assertEquals(interrupted.getRefundOperationKey(), recovered.getRefundOperationKey());
        assertEquals(PaymentStatus.REFUNDED, recovered.getStatus());
        assertStock(98, 0);
        verify(gateway, times(1)).refundPayment(any(PaymentGatewayRefundRequest.class));
    }


    @Test
    void refundReconciliationAdvancesPastUnknownSameProviderRowsAndFiltersOldProvider() {
        Order inertOrder = orderRepository.save(Order.builder().user(owner).orderNumber("REF-" + key().substring(0, 16))
                .status(OrderStatus.PAID).paymentAmount(new BigDecimal("20.00")).build());
        List<Payment> blocked = new ArrayList<>();
        for (String provider : List.of("retired-provider", "stub")) {
            for (int i = 0; i < 100; i++) {
                blocked.add(Payment.builder().order(inertOrder).provider(provider).status(PaymentStatus.SUCCEEDED)
                        .externalId(key()).amount(new BigDecimal("20.00")).currency("EUR").idempotencyKey(key())
                        .refundOperationKey(key()).refundStatus(PaymentGatewayRefundStatus.UNKNOWN).build());
            }
        }
        paymentRepository.saveAll(blocked);
        Set<Long> oldProviderIds = new HashSet<>();
        blocked.stream().filter(p -> p.getProvider().equals("retired-provider")).forEach(p -> oldProviderIds.add(p.getId()));
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class)))
                .thenReturn(new PaymentGatewayRefundResponse(null, PaymentGatewayRefundStatus.UNKNOWN));
        assertThrows(ServiceUnavailableException.class, () -> payments.refundPayment(payment.id()));
        AtomicInteger lookups = new AtomicInteger();
        when(gateway.fetchRefundStatus(any(), nullable(String.class))).thenAnswer(call -> {
            PaymentGatewayRefundRequest request = call.getArgument(0);
            assertFalse(oldProviderIds.contains(request.paymentId()), "provider filtering must happen before selecting the batch");
            lookups.incrementAndGet();
            return request.paymentId().equals(payment.id())
                    ? Optional.of(new PaymentGatewayRefundResponse("refund-" + payment.id(), PaymentGatewayRefundStatus.SUCCEEDED))
                    : Optional.empty();
        });
        try {
            payments.reconcileRefunds();
            int firstBatch = lookups.get();
            assertTrue(firstBatch <= 100);
            payments.reconcileRefunds();
            assertTrue(lookups.get() - firstBatch <= 100);
            assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(payment.id()).orElseThrow().getStatus());
            assertStock(98, 0);
        } finally {
            blocked.forEach(p -> p.setRefundStatus(PaymentGatewayRefundStatus.FAILED));
            paymentRepository.saveAll(blocked);
        }
    }


    @Test
    void definitiveCreateRejectionFailsAttemptAndAllowsFreshPayment() {
        Order order = order(owner, 1);
        when(gateway.createPayment(any())).thenThrow(new PaymentGatewayCreateException("invalid provider request"));
        String rejectedKey = key();
        assertThrows(BusinessRuleException.class, () -> payments.createPayment(order.getId(), rejectedKey));
        assertEquals(PaymentStatus.FAILED, payments.createPayment(order.getId(), rejectedKey).status());
        assertEquals(OrderStatus.CREATED, orderRepository.findById(order.getId()).orElseThrow().getStatus());
        // doReturn: the current stub throws, so when(gateway.createPayment(...)) itself would raise it.
        doReturn(new PaymentGatewayCreateResponse(key(), "stub", PaymentStatus.PENDING, "/demo", null)).when(gateway).createPayment(any());
        assertEquals(PaymentStatus.PENDING, payments.createPayment(order.getId(), key()).status());
        assertStock(98, 1);
    }

    @Test
    void recoveryUsesPersistedCreateBodyAndKeyAndDoesNotRegressFastCallback() {
        Order order = order(owner, 1);
        String originalContext = "exact-original-body";
        when(gateway.snapshotCreateContext(any())).thenReturn(originalContext);
        when(gateway.createPayment(any())).thenThrow(new IllegalStateException("unknown response"));
        String paymentKey = key();
        assertThrows(ServiceUnavailableException.class, () -> payments.createPayment(order.getId(), paymentKey));
        Payment created = paymentRepository.findByIdempotencyKey(paymentKey).orElseThrow();
        jdbc.update("update payments set created_at = now() - interval '2 minutes' where id = ?", created.getId());
        when(gateway.snapshotCreateContext(any())).thenReturn("different-current-config-body");
        when(gateway.recoverPayment(argThat(request -> request.paymentId().equals(created.getId())), any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            PaymentGatewayCreateRequest request = call.getArgument(0);
            assertEquals(originalContext, request.providerRequestContext());
            assertEquals(paymentKey, request.idempotencyKey());
            Instant preparedAt = call.getArgument(1);
            Instant retryUntil = call.getArgument(2);
            assertEquals(preparedAt.plusSeconds(23 * 3600), retryUntil);
            String external = key();
            payments.applyVerifiedCallback("stub", new PaymentCallbackRequest(key(), external, PaymentStatus.SUCCEEDED, "fast", request.paymentId()));
            return Optional.of(new PaymentGatewayCreateResponse(external, "stub", PaymentStatus.PENDING, "/recovered", null));
        });
        payments.reconcileCreates();
        Payment recovered = paymentRepository.findById(created.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, recovered.getStatus());
        assertEquals("/recovered", recovered.getPaymentUrl());
        assertStock(97, 0);
        verify(gateway, times(1)).createPayment(any());
    }

    @Test
    void createRecoveryNeverReplaysAfterTheProviderIdempotencyWindow() {
        Order order = order(owner, 1);
        when(gateway.snapshotCreateContext(any())).thenReturn("original-body");
        when(gateway.createPayment(any())).thenThrow(new IllegalStateException("unknown response"));
        String paymentKey = key();
        assertThrows(ServiceUnavailableException.class, () -> payments.createPayment(order.getId(), paymentKey));
        Payment created = paymentRepository.findByIdempotencyKey(paymentKey).orElseThrow();
        jdbc.update("update payments set created_at = now() - interval '24 hours' where id = ?", created.getId());
        payments.reconcileCreates();
        verify(gateway, never()).recoverPayment(argThat(request -> request.paymentId().equals(created.getId())), any(), any());
        assertEquals(PaymentStatus.CREATED, paymentRepository.findById(created.getId()).orElseThrow().getStatus());
        assertStock(98, 1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = PaymentGatewayRefundStatus.class, names = {"FAILED", "CANCELED"})
    void refundRetryRequiresFailedKeyKeepsHistoryAndIgnoresLateOldCallback(PaymentGatewayRefundStatus definitiveStatus) {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class)))
                .thenReturn(new PaymentGatewayRefundResponse("failed-refund", definitiveStatus));
        PaymentResponse failed = payments.refundPayment(payment.id());
        String oldKey = failed.refundOperationKey();
        payments.refundPayment(payment.id());
        verify(gateway, times(1)).refundPayment(any(PaymentGatewayRefundRequest.class));
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class)))
                .thenReturn(new PaymentGatewayRefundResponse("new-refund", PaymentGatewayRefundStatus.PENDING));
        PaymentResponse retried = payments.retryRefundPayment(payment.id(), oldKey);
        assertNotEquals(oldKey, retried.refundOperationKey());
        payments.retryRefundPayment(payment.id(), oldKey);
        verify(gateway, times(2)).refundPayment(any(PaymentGatewayRefundRequest.class));
        payments.applyVerifiedRefundCallback("stub", payment.id(), oldKey, key(),
                new PaymentGatewayRefundResponse("failed-refund", PaymentGatewayRefundStatus.SUCCEEDED));
        assertEquals(PaymentGatewayRefundStatus.PENDING, paymentRepository.findById(payment.id()).orElseThrow().getRefundStatus());
        assertStock(97, 0);
        payments.applyVerifiedRefundCallback("stub", payment.id(), retried.refundOperationKey(), key(),
                new PaymentGatewayRefundResponse("new-refund", PaymentGatewayRefundStatus.SUCCEEDED));
        assertStock(98, 0);
        assertEquals(definitiveStatus.name(), jdbc.queryForObject("select status from payment_refund_attempts where operation_key = ?", String.class, oldKey));
        assertEquals("SUCCEEDED", jdbc.queryForObject("select status from payment_refund_attempts where operation_key = ?", String.class, retried.refundOperationKey()));
    }

    @Test
    void unknownRefundCannotStartAnExplicitNewAttemptAndProviderCallbackIsBound() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class)))
                .thenReturn(new PaymentGatewayRefundResponse(null, PaymentGatewayRefundStatus.UNKNOWN));
        assertThrows(ServiceUnavailableException.class, () -> payments.refundPayment(payment.id()));
        PaymentResponse unknown = payments.getPaymentForCurrentUser(payment.id());
        assertThrows(BusinessRuleException.class, () -> payments.retryRefundPayment(payment.id(), unknown.refundOperationKey()));
        assertThrows(ForbiddenOperationException.class, () -> payments.applyVerifiedRefundCallback("stripe", payment.id(), unknown.refundOperationKey(), key(),
                new PaymentGatewayRefundResponse("wrong", PaymentGatewayRefundStatus.SUCCEEDED)));
        verify(gateway, times(1)).refundPayment(any(PaymentGatewayRefundRequest.class));
        assertStock(97, 0);
    }


    @Test
    void fastRefundFailureCallbackCannotBeRegressedByAcceptedHttpResponse() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class))).thenAnswer(call -> {
            PaymentGatewayRefundRequest request = call.getArgument(0);
            payments.applyVerifiedRefundCallback("stub", payment.id(), request.idempotencyKey(), key(),
                    new PaymentGatewayRefundResponse("refund-fast", PaymentGatewayRefundStatus.FAILED));
            return new PaymentGatewayRefundResponse("refund-fast", PaymentGatewayRefundStatus.PENDING);
        });
        assertEquals(PaymentGatewayRefundStatus.FAILED, payments.refundPayment(payment.id()).refundStatus());
        assertStock(97, 0);
    }

    @Test
    void lateHttpResponseFromPreviousRefundCannotCompleteItsReplacement() {
        Order order = order(owner, 1);
        PaymentResponse payment = payments.createPayment(order.getId(), key());
        callback(payment.externalPaymentId(), key(), PaymentStatus.SUCCEEDED);
        login(admin());
        AtomicInteger calls = new AtomicInteger();
        when(gateway.refundPayment(any(PaymentGatewayRefundRequest.class))).thenAnswer(call -> {
            PaymentGatewayRefundRequest request = call.getArgument(0);
            if (calls.incrementAndGet() == 1) {
                payments.applyVerifiedRefundCallback("stub", payment.id(), request.idempotencyKey(), key(),
                        new PaymentGatewayRefundResponse("old-refund", PaymentGatewayRefundStatus.FAILED));
                payments.retryRefundPayment(payment.id(), request.idempotencyKey());
                return new PaymentGatewayRefundResponse("old-refund", PaymentGatewayRefundStatus.SUCCEEDED);
            }
            return new PaymentGatewayRefundResponse("new-refund", PaymentGatewayRefundStatus.PENDING);
        });
        PaymentResponse response = payments.refundPayment(payment.id());
        assertEquals(2, calls.get());
        assertEquals(PaymentStatus.SUCCEEDED, response.status());
        assertEquals(PaymentGatewayRefundStatus.PENDING, response.refundStatus());
        assertEquals("new-refund", paymentRepository.findById(payment.id()).orElseThrow().getRefundExternalId());
        assertStock(97, 0);
    }

    private User admin() {
        User admin = user();
        admin.setRoles(new HashSet<>(List.of(roles.findByName("ADMIN").orElseThrow())));
        return users.save(admin);
    }

    private Order order(User user, int quantity) {
        Cart cart = carts.findByUser(user).orElseGet(() -> carts.save(Cart.builder().user(user).build()));
        cartItems.save(CartItem.builder().cart(cart).variant(variant).quantity(quantity).build());
        Order order = orders.createOrderFromCart(user, key());
        shipments.save(Shipment.builder().order(order).method(ShippingMethod.LOCAL_PICKUP)
                .status(ShippingStatus.PENDING).cost(BigDecimal.ZERO).currency("EUR").build());
        return order;
    }

    private void callback(String external, String event, PaymentStatus status) {
        payments.handleCallback(new PaymentCallbackRequest(event, external, status, "test"), "payload", "valid");
    }
    private User user() { return users.save(User.builder().email(key() + "@example.com").passwordHash("test")
            .enabled(true).build()); }
    private void login(User user) { SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user.getEmail(), "", List.of())); }
    private String key() { return UUID.randomUUID().toString(); }
    private void assertStock(int stock, int reserved) {
        var row = inventoryRepository.findByVariant_Id(variant.getId()).orElseThrow();
        assertEquals(stock, row.getStock());
        assertEquals(reserved, row.getReserved());
    }
    private <T> List<T> concurrent(int count, java.util.function.IntFunction<T> action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(count)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> { assertTrue(start.await(10, TimeUnit.SECONDS)); return action.apply(index); }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
}
