package com.example.shopupu.orders.controller;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.mapper.OrderMapper;
import com.example.shopupu.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RU: Контроллер для заказов
 * EN: REST controller for orders
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final OrderMapper orderMapper;

    // Создать заказ из корзины
    @PostMapping("/checkout")
    public ResponseEntity<OrderDto> createOrder(Authentication authentication) {
        String email = authentication.getName();
        User user = userService.getByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found - " + email));

        var order = orderService.createOrderFromCart(user);
        return ResponseEntity.ok(orderMapper.toDto(order));
    }

    // Список заказов пользователя с фильтром по статусу
    @GetMapping
    public ResponseEntity<List<OrderDto>> getOrders(
            Authentication authentication,
            @RequestParam(required = false) String status
    ) {
        String email = authentication.getName();
        User user = userService.getByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found - " + email));

        var orders = orderService.getOrdersForUser(user, status);
        var dtoList = orders.stream().map(orderMapper::toDto).toList();
        return ResponseEntity.ok(dtoList);
    }

    // Просмотр конкретного заказа
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderMapper.toDto(orderService.getOrder(id)));
    }

    // Обновить статус
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        var updated = orderService.updateStatus(id, status);
        return ResponseEntity.ok(orderMapper.toDto(updated));
    }
}