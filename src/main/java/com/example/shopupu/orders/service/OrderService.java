package com.example.shopupu.orders.service;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderItem;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final AccessControlService accessControlService;
    private final CartRepository cartRepository;

    @Transactional
    public Order createOrderFromCart(User user) {
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + user.getEmail()));

        List<CartItem> cartItems = cartItemRepository.findByCart(cart);
        if (cartItems.isEmpty()) {
            throw new BusinessRuleException("Cart is empty - nothing to order");
        }

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.NEW);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            validateAndReserveStock(cartItem);

            Product product = cartItem.getProduct();
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            items.add(OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .title(product.getTitle())
                    .price(product.getPrice())
                    .quantity(cartItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build());
        }

        order.setItems(items);
        order.setSubtotalAmount(subtotal);
        order.setShippingAmount(BigDecimal.ZERO);
        order.setPaymentAmount(subtotal);

        Order savedOrder = orderRepository.save(order);
        cartItemRepository.deleteAll(cartItems);
        return savedOrder;
    }

    public List<Order> getOrdersForUser(User user, String status) {
        List<Order> orders = orderRepository.findByUser(user);
        if (status == null || status.isBlank()) {
            return orders;
        }

        List<Order> filteredOrders = new ArrayList<>();
        for (Order order : orders) {
            if (order.getStatus().name().equalsIgnoreCase(status)) {
                filteredOrders.add(order);
            }
        }
        return filteredOrders;
    }

    public List<Order> getAllOrders() {
        accessControlService.requireAdmin();
        return orderRepository.findAll();
    }

    public Order getOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found - " + id));
    }

    public Order getOrderForCurrentUser(Long id) {
        Order order = getOrder(id);
        accessControlService.requireOrderOwnerOrAdmin(order);
        return order;
    }

    @Transactional
    public Order updateStatus(Long id, String newStatusString) {
        accessControlService.requireAdmin();
        Order order = getOrder(id);
        OrderStatus newStatus;

        try {
            newStatus = OrderStatus.valueOf(newStatusString.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid order status: " + newStatusString);
        }

        return applyStatus(order, newStatus);
    }

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrder(id);
        accessControlService.requireOrderOwnerOrAdmin(order);
        return applyStatus(order, OrderStatus.CANCELED);
    }

    @Transactional
    public Order markPaidFromPayment(Long id) {
        Order order = getOrder(id);
        return applyStatus(order, OrderStatus.PAID);
    }

    @Transactional
    public Order updateShippingAmount(Long id, BigDecimal shippingAmount) {
        Order order = getOrder(id);
        if (order.getStatus() != OrderStatus.NEW) {
            throw new BusinessRuleException("Shipping can only be changed for NEW orders");
        }
        BigDecimal normalizedShipping = shippingAmount == null ? BigDecimal.ZERO : shippingAmount;
        order.setShippingAmount(normalizedShipping);
        order.setPaymentAmount(order.getSubtotalAmount().add(normalizedShipping));
        return orderRepository.save(order);
    }

    private Order applyStatus(Order order, OrderStatus newStatus) {
        OrderStatus current = order.getStatus();

        if (!isStatusChangeAllowed(current, newStatus)) {
            throw new BusinessRuleException("Order status " + newStatus + " is not allowed from " + current);
        }

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    private boolean isStatusChangeAllowed(OrderStatus current, OrderStatus next) {
        if (current == OrderStatus.NEW) {
            return next == OrderStatus.PAID || next == OrderStatus.CANCELED;
        }
        if (current == OrderStatus.PAID) {
            return next == OrderStatus.SHIPPED || next == OrderStatus.CANCELED;
        }
        if (current == OrderStatus.SHIPPED) {
            return next == OrderStatus.COMPLETED;
        }
        return false;
    }

    private void validateAndReserveStock(CartItem item) {
        Product product = item.getProduct();
        if (!Boolean.TRUE.equals(product.getEnabled())) {
            throw new BusinessRuleException("Product is disabled: " + product.getId());
        }
        if (product.getStock() == null || product.getStock() < item.getQuantity()) {
            throw new BusinessRuleException("Not enough stock for product: " + product.getId());
        }
        product.setStock(product.getStock() - item.getQuantity());
    }
}
