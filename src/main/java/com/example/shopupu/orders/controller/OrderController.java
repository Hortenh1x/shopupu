package com.example.shopupu.orders.controller;

import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.mapper.OrderMapper;
import com.example.shopupu.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
/**
 * describes the OrderController class.
 */
public class OrderController {

    private final OrderService orderService;
    private final AccessControlService accessControlService;
    private final OrderMapper orderMapper;


    @PostMapping("/checkout")
    // handles createOrder.
    public ResponseEntity<OrderDto> createOrder() {
        User user = accessControlService.currentUser();
        var order = orderService.createOrderFromCart(user);
        return ResponseEntity.ok(orderMapper.toDto(order));
    }


    @GetMapping
    // handles getOrders.
    public ResponseEntity<List<OrderDto>> getOrders(
            @RequestParam(required = false) String status
    ) {
        User user = accessControlService.currentUser();
        var orders = orderService.getOrdersForUser(user, status);
        var dtoList = orders.stream().map(orderMapper::toDto).toList();
        return ResponseEntity.ok(dtoList);
    }


    @GetMapping("/{id}")
    // handles getOrderById.
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderMapper.toDto(orderService.getOrderForCurrentUser(id)));
    }

    @PatchMapping("/{id}/cancel")
    // handles cancelOrder.
    public ResponseEntity<OrderDto> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderMapper.toDto(orderService.cancelOrder(id)));
    }

}