package com.example.shopupu.orders.service;

import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderItem;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RU: Сервис для работы с заказами.
 * EN: Service layer for handling orders.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;

    // Разрешённые переходы статусов (state machine)
    private static final Map<OrderStatus, Set<OrderStatus>> allowedTransitions = new EnumMap<>(OrderStatus.class);
    static {
        allowedTransitions.put(OrderStatus.NEW, Set.of(OrderStatus.PAID, OrderStatus.CANCELED));
        allowedTransitions.put(OrderStatus.PAID, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELED));
        allowedTransitions.put(OrderStatus.SHIPPED, Set.of(OrderStatus.COMPLETED));
        allowedTransitions.put(OrderStatus.COMPLETED, Set.of());
        allowedTransitions.put(OrderStatus.CANCELED, Set.of());
    }

    /**
     * RU: Создаёт заказ из содержимого корзины.
     * EN: Creates a new order from the user's cart contents.
     */
    @Transactional
    public Order createOrderFromCart(User user) {
        // Получаем все товары из корзины пользователя
        List<CartItem> cartItems = cartItemRepository.findByUser(user);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty - nothing to order");
        }

        // Создаём новый заказ
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.NEW);

        // Преобразуем позиции корзины в позиции заказа
        var items = cartItems.stream().map(ci -> {
                    var product = ci.getProduct();
                    var price = product.getPrice();
                    var lineTotal = price.multiply(BigDecimal.valueOf(ci.getQuantity()));

                    return OrderItem.builder()
                            .order(order)
                            .productId(product.getId())
                            .title(product.getTitle())
                            .price(product.getPrice())
                            .quantity(ci.getQuantity())
                            .lineTotal(lineTotal)
                            .build();
                }).collect(Collectors.toList());

        // Рассчитываем общую сумму
        order.setItems(items);
        order.setTotalAmount(
                items.stream()
                        .map(OrderItem::getLineTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        var saved = orderRepository.save(order);

        cartItemRepository.deleteAll(cartItems);

        return saved;
    }

    /**
     * RU: Получить все заказы пользователя.
     * EN: Fetch all orders for a given user.
     */
    public List<Order> getOrdersForUser(User user, String status) {
        var all = orderRepository.findByUser(user);
        if (status == null || status.isBlank()) return all;
        return all.stream()
                .filter(order -> order.getStatus().name().equalsIgnoreCase(status))
                .toList();
    }

    /**
     * RU: Получить заказ по ID.
     * EN: Get order by ID.
     */
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found - " + id));
    }

    @Transactional
    public Order updateStatus(Long id, String newStatusString) {
        Order order = getOrder(id);
        OrderStatus newStatus;

        try {
            newStatus = OrderStatus.valueOf(newStatusString.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid order status: " + newStatusString);
        }

        OrderStatus current = order.getStatus();
        Set<OrderStatus> allowed = allowedTransitions.getOrDefault(current, Set.of());

        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException("Order status " + newStatus + " is not allowed");
        }

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }
}