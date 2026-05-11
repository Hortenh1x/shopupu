package com.example.shopupu.orders.service;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * describes the OrderServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private CartRepository cartRepository;

    private OrderService orderService;
    private User user;

    // handles setUp.
    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartItemRepository, accessControlService, cartRepository);
        user = User.builder().id(1L).email("user@example.com").build();
    }

    // handles createOrderFromCart.
    @Test
    void createOrderFromCartCreatesOrderReservesStockAndClearsCart() {
        Cart cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
        Product product = product(100L, true, 5);
        CartItem item = CartItem.builder().cart(cart).product(product).quantity(2).build();
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(item));
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.createOrderFromCart(user);

        assertEquals(OrderStatus.NEW, order.getStatus());
        assertEquals(new BigDecimal("20.00"), order.getSubtotalAmount());
        assertEquals(new BigDecimal("20.00"), order.getPaymentAmount());
        assertEquals(3, product.getStock());
        assertEquals(1, order.getItems().size());
        verify(cartItemRepository).deleteAll(List.of(item));
    }

    // handles createOrderFromCart.
    @Test
    void createOrderFromCartRejectsMissingCartEmptyCartDisabledProductAndInsufficientStock() {
        when(cartRepository.findByUser(user)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrderFromCart(user));

        Cart cart = Cart.builder().id(10L).user(user).build();
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of());
        assertThrows(BusinessRuleException.class, () -> orderService.createOrderFromCart(user));

        CartItem disabled = CartItem.builder().cart(cart).product(product(100L, false, 5)).quantity(1).build();
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(disabled));
        assertThrows(BusinessRuleException.class, () -> orderService.createOrderFromCart(user));

        CartItem outOfStock = CartItem.builder().cart(cart).product(product(100L, true, 1)).quantity(2).build();
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(outOfStock));
        assertThrows(BusinessRuleException.class, () -> orderService.createOrderFromCart(user));
    }

    // handles getOrdersForUser.
    @Test
    void getOrdersForUserReturnsAllOrFilteredOrders() {
        Order newOrder = order(1L, OrderStatus.NEW);
        Order paidOrder = order(2L, OrderStatus.PAID);
        when(orderRepository.findByUser(user)).thenReturn(List.of(newOrder, paidOrder));

        assertEquals(2, orderService.getOrdersForUser(user, null).size());
        assertEquals(List.of(paidOrder), orderService.getOrdersForUser(user, "paid"));
    }

    // handles getAllOrders.
    @Test
    void getAllOrdersRequiresAdminAndReturnsRepositoryOrders() {
        Order order = order(1L, OrderStatus.NEW);
        when(orderRepository.findAll()).thenReturn(List.of(order));

        assertEquals(List.of(order), orderService.getAllOrders());
        verify(accessControlService).requireAdmin();
    }

    // handles getOrder.
    @Test
    void getOrderReturnsOrderOrThrowsWhenMissing() {
        Order order = order(1L, OrderStatus.NEW);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.findWithItemsById(2L)).thenReturn(Optional.empty());

        assertSame(order, orderService.getOrder(1L));
        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrder(2L));
    }

    // handles getOrderForCurrentUser.
    @Test
    void getOrderForCurrentUserChecksAccess() {
        Order order = order(1L, OrderStatus.NEW);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertSame(order, orderService.getOrderForCurrentUser(1L));
        verify(accessControlService).requireOrderOwnerOrAdmin(order);
    }

    // handles updateStatus.
    @Test
    void updateStatusRequiresAdminAndAppliesAllowedTransition() {
        Order order = order(1L, OrderStatus.NEW);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order updated = orderService.updateStatus(1L, "paid");

        assertEquals(OrderStatus.PAID, updated.getStatus());
        verify(accessControlService).requireAdmin();
    }

    // handles updateStatus.
    @Test
    void updateStatusRejectsInvalidStatusAndInvalidTransition() {
        Order order = order(1L, OrderStatus.CANCELED);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        assertThrows(BusinessRuleException.class, () -> orderService.updateStatus(1L, "paid"));

        assertThrows(BusinessRuleException.class, () -> orderService.updateStatus(1L, "unknown"));
    }

    // handles cancelOrder.
    @Test
    void cancelOrderChecksAccessAndCancelsNewOrder() {
        Order order = order(1L, OrderStatus.NEW);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order canceled = orderService.cancelOrder(1L);

        assertEquals(OrderStatus.CANCELED, canceled.getStatus());
        verify(accessControlService).requireOrderOwnerOrAdmin(order);
    }

    // handles markPaidFromPayment.
    @Test
    void markPaidFromPaymentMarksNewOrderAsPaid() {
        Order order = order(1L, OrderStatus.NEW);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        assertEquals(OrderStatus.PAID, orderService.markPaidFromPayment(1L).getStatus());
    }

    // handles updateShippingAmount.
    @Test
    void updateShippingAmountUpdatesPaymentAmountForNewOrder() {
        Order order = order(1L, OrderStatus.NEW);
        order.setSubtotalAmount(new BigDecimal("20.00"));
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order updated = orderService.updateShippingAmount(1L, new BigDecimal("4.99"));

        assertEquals(new BigDecimal("4.99"), updated.getShippingAmount());
        assertEquals(new BigDecimal("24.99"), updated.getPaymentAmount());
    }

    // handles updateShippingAmount.
    @Test
    void updateShippingAmountRejectsNonNewOrders() {
        Order order = order(1L, OrderStatus.PAID);
        when(orderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThrows(BusinessRuleException.class, () -> orderService.updateShippingAmount(1L, BigDecimal.ONE));
    }

    private Order order(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setStatus(status);
        order.setSubtotalAmount(BigDecimal.ZERO);
        order.setShippingAmount(BigDecimal.ZERO);
        order.setPaymentAmount(BigDecimal.ZERO);
        return order;
    }

    private Product product(Long id, boolean enabled, int stock) {
        Category category = new Category("Phones", "phones", null, null);
        category.setId(1L);
        Product product = new Product("Phone", "desc", new BigDecimal("10.00"), "sku-" + id, stock, category);
        product.setId(id);
        product.setEnabled(enabled);
        return product;
    }
}
