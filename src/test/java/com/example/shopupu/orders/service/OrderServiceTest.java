package com.example.shopupu.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Brand;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.OutOfStockException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.config.CheckoutProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.inventory.service.InventoryService;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderItem;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.entity.OrderStatusHistory;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private com.example.shopupu.promo.service.PromoService promoService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.example.shopupu.common.audit.AuditService auditService;

    private OrderService orderService;
    private User user;

    @BeforeEach
    void setUp() {
        CheckoutProperties checkoutProperties = new CheckoutProperties();
        orderService = new OrderService(
                orderRepository,
                statusHistoryRepository,
                cartItemRepository,
                cartRepository,
                accessControlService,
                inventoryService,
                orderNumberGenerator,
                checkoutProperties,
                promoService,
                eventPublisher,
                auditService,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                new com.example.shopupu.orders.mapper.OrderMapperImpl()
        );
        user = User.builder().id(1L).email("user@example.com").build();
    }

    @Test
    void checkoutReservesStockAndSnapshotsVariantData() {
        Cart cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
        ProductVariant variant = variant(100L, "SKU-M-BLK", "M", "black", new BigDecimal("25.00"));
        CartItem item = CartItem.builder().cart(cart).variant(variant).quantity(2).build();
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(item));
        when(orderNumberGenerator.next()).thenReturn("ORD-20260706-TEST01");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.createOrderFromCart(user, null);

        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals("ORD-20260706-TEST01", order.getOrderNumber());
        assertEquals(new BigDecimal("50.00"), order.getSubtotalAmount());
        assertEquals(new BigDecimal("50.00"), order.getPaymentAmount());
        assertEquals(1, order.getItems().size());

        OrderItem orderItem = order.getItems().get(0);
        assertEquals("SKU-M-BLK", orderItem.getSku());
        assertEquals("M", orderItem.getSize());
        assertEquals("black", orderItem.getColor());
        assertEquals("TestBrand", orderItem.getBrand());
        assertEquals("Hoodie", orderItem.getTitle());
        assertEquals(new BigDecimal("25.00"), orderItem.getPrice());
        assertEquals(100L, orderItem.getVariantId());

        verify(inventoryService).reserve(eq(100L), eq(2), anyString());
        verify(cartItemRepository).deleteAll(List.of(item));
        verify(statusHistoryRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    void checkoutIsIdempotentPerKey() {
        Order existing = order(5L, OrderStatus.CREATED);
        when(orderRepository.findByUserAndIdempotencyKey(user, "key-1")).thenReturn(Optional.of(existing));

        Order result = orderService.createOrderFromCart(user, "key-1");

        assertSame(existing, result);
        verifyNoInteractions(inventoryService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkoutPropagatesOutOfStock() {
        Cart cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
        ProductVariant variant = variant(100L, "SKU-M-BLK", "M", "black", new BigDecimal("25.00"));
        CartItem item = CartItem.builder().cart(cart).variant(variant).quantity(2).build();
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(item));
        when(orderNumberGenerator.next()).thenReturn("ORD-20260706-TEST02");
        doThrow(new OutOfStockException("Not enough stock for variant: 100"))
                .when(inventoryService).reserve(eq(100L), eq(2), anyString());

        assertThrows(OutOfStockException.class, () -> orderService.createOrderFromCart(user, null));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkoutRejectsEmptyCart() {
        Cart cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of());

        assertThrows(BusinessRuleException.class, () -> orderService.createOrderFromCart(user, null));
    }

    @Test
    void cancelFromCreatedReleasesReservation() {
        Order order = orderWithItem(7L, OrderStatus.CREATED);
        when(orderRepository.findWithItemsById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessControlService.currentEmail()).thenReturn("user@example.com");

        Order cancelled = orderService.cancelOrder(7L);

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        verify(accessControlService).requireOrderOwnerOrAdmin(order);
        verify(inventoryService).release(eq(100L), eq(2), anyString());
        verify(statusHistoryRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    void cancelRejectedOncePaid() {
        Order order = orderWithItem(7L, OrderStatus.PAID);
        when(orderRepository.findWithItemsById(7L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.cancelOrder(7L));
        verify(inventoryService, never()).release(anyLong(), anyInt(), anyString());
    }

    @Test
    void markPaidCommitsSale() {
        Order order = orderWithItem(7L, OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findWithItemsById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order paid = orderService.markPaidFromPayment(7L);

        assertEquals(OrderStatus.PAID, paid.getStatus());
        verify(inventoryService).commitSale(eq(100L), eq(2), anyString());
    }

    @Test
    void markRefundedRestocksItems() {
        Order order = orderWithItem(7L, OrderStatus.PAID);
        when(orderRepository.findWithItemsById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order refunded = orderService.markRefunded(7L, "admin@example.com");

        assertEquals(OrderStatus.REFUNDED, refunded.getStatus());
        verify(inventoryService).restock(eq(100L), eq(2), anyString());

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(statusHistoryRepository).save(historyCaptor.capture());
        assertEquals("admin@example.com", historyCaptor.getValue().getChangedBy());
        assertEquals(OrderStatus.PAID, historyCaptor.getValue().getFromStatus());
        assertEquals(OrderStatus.REFUNDED, historyCaptor.getValue().getToStatus());
    }

    @Test
    void illegalTransitionRejected() {
        Order order = orderWithItem(7L, OrderStatus.CREATED);
        when(orderRepository.findWithItemsById(7L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class,
                () -> orderService.updateStatus(7L, OrderStatus.SHIPPED));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void adminStatusUpdateRecordsActor() {
        Order order = orderWithItem(7L, OrderStatus.PAID);
        when(orderRepository.findWithItemsById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessControlService.currentEmail()).thenReturn("admin@example.com");

        Order updated = orderService.updateStatus(7L, OrderStatus.PROCESSING);

        assertEquals(OrderStatus.PROCESSING, updated.getStatus());
        verify(accessControlService).requireAdmin();

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(statusHistoryRepository).save(historyCaptor.capture());
        assertEquals("admin@example.com", historyCaptor.getValue().getChangedBy());
    }

    @Test
    void updateShippingAmountOnlyBeforePayment() {
        Order created = orderWithItem(7L, OrderStatus.CREATED);
        created.setSubtotalAmount(new BigDecimal("50.00"));
        when(orderRepository.findWithItemsById(7L)).thenReturn(Optional.of(created));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order updated = orderService.updateShippingAmount(7L, new BigDecimal("4.99"));
        assertEquals(new BigDecimal("54.99"), updated.getPaymentAmount());

        Order paid = orderWithItem(8L, OrderStatus.PAID);
        when(orderRepository.findWithItemsById(8L)).thenReturn(Optional.of(paid));
        assertThrows(BusinessRuleException.class,
                () -> orderService.updateShippingAmount(8L, new BigDecimal("4.99")));
    }

    @Test
    void expireStaleOrdersCancelsAndReleases() {
        Order stale = orderWithItem(9L, OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findTop100ByStatusInAndCreatedAtBefore(any(Collection.class), any(Instant.class)))
                .thenReturn(List.of(stale));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int expired = orderService.expireStaleOrders();

        assertEquals(1, expired);
        assertEquals(OrderStatus.CANCELLED, stale.getStatus());
        verify(inventoryService).release(eq(100L), eq(2), anyString());
    }

    private ProductVariant variant(Long id, String sku, String size, String color, BigDecimal price) {
        Category category = new Category("Hoodies", "hoodies", null, null);
        Product product = new Product("Hoodie", "hoodie", "desc", price, category);
        product.setId(50L);
        product.setEnabled(true);
        Brand brand = new Brand("TestBrand", "testbrand");
        product.setBrand(brand);
        return ProductVariant.builder()
                .id(id)
                .product(product)
                .sku(sku)
                .size(size)
                .color(color)
                .price(price)
                .enabled(true)
                .build();
    }

    private Order order(Long id, OrderStatus status) {
        return Order.builder()
                .id(id)
                .orderNumber("ORD-20260706-EX" + id)
                .user(user)
                .status(status)
                .build();
    }

    private Order orderWithItem(Long id, OrderStatus status) {
        Order order = order(id, status);
        OrderItem item = OrderItem.builder()
                .order(order)
                .productId(50L)
                .variantId(100L)
                .title("Hoodie")
                .sku("SKU-M-BLK")
                .size("M")
                .color("black")
                .price(new BigDecimal("25.00"))
                .quantity(2)
                .lineTotal(new BigDecimal("50.00"))
                .build();
        order.getItems().add(item);
        return order;
    }
}
