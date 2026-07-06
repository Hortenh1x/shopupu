package com.example.shopupu.orders.controller;

import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.mapper.OrderMapper;
import com.example.shopupu.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final AccessControlService accessControlService;
    private final OrderMapper orderMapper;

    @PostMapping("/checkout")
    public ResponseEntity<OrderDto> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @jakarta.validation.Valid @RequestBody(required = false) com.example.shopupu.orders.dto.CheckoutRequest request) {
        User user = accessControlService.currentUser();
        String promoCode = request == null ? null : request.promoCode();
        var order = orderService.createOrderFromCart(user, idempotencyKey, promoCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderMapper.toDto(order));
    }

    @GetMapping
    public ResponseEntity<Page<OrderDto>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        User user = accessControlService.currentUser();
        return ResponseEntity.ok(orderService.getOrdersForUser(user, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderMapper.toDto(orderService.getOrderForCurrentUser(id)));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderDto> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderMapper.toDto(orderService.cancelOrder(id)));
    }
}
