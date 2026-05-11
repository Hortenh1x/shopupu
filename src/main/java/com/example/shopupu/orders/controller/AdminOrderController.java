package com.example.shopupu.orders.controller;

import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.mapper.OrderMapper;
import com.example.shopupu.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
/**
 * describes the AdminOrderController class.
 */
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @GetMapping
    // handles getOrders.
    public ResponseEntity<List<OrderDto>> getOrders() {
        var orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders.stream().map(orderMapper::toDto).toList());
    }

    @GetMapping("/{id}")
    // handles getOrder.
    public ResponseEntity<OrderDto> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderMapper.toDto(orderService.getOrder(id)));
    }

    @PatchMapping("/{id}/status")
    // handles updateOrderStatus.
    public ResponseEntity<OrderDto> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        var updated = orderService.updateStatus(id, status);
        return ResponseEntity.ok(orderMapper.toDto(updated));
    }
}