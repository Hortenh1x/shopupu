package com.example.shopupu.orders.controller;

import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.dto.OrderStatusHistoryDto;
import com.example.shopupu.orders.dto.UpdateOrderStatusRequest;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/orders")
// Order administration is ADMIN-only; the service layer also enforces requireAdmin().
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<Page<OrderDto>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderResponse(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<OrderStatusHistoryDto>> getOrderHistory(@PathVariable Long id) {
        var history = orderService.getStatusHistoryResponses(id);
        return ResponseEntity.ok(history);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return ResponseEntity.ok(orderService.updateStatusResponse(id, request.status()));
    }
}
