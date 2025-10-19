package com.example.shopupu.shipping.controller;

import com.example.shopupu.shipping.dto.ShipmentDto;
import com.example.shopupu.shipping.dto.ShippingRequests;
import com.example.shopupu.shipping.entity.ShippingStatus;
import com.example.shopupu.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping("/address")
    public ResponseEntity<ShipmentDto> setAddress(@RequestBody ShippingRequests.SetAddress req) {
        return ResponseEntity.ok(shippingService.setAddress(req));
    }

    @PostMapping("/method")
    public ResponseEntity<ShipmentDto> setMethod(@RequestBody ShippingRequests.SetMethod req) {
        return ResponseEntity.ok(shippingService.setMethod(req));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ShipmentDto> get(@PathVariable Long orderId) {
        return ResponseEntity.ok(shippingService.getByOrder(orderId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ShipmentDto> updateStatus(
            @PathVariable Long orderId,
            @RequestParam ShippingStatus status,
            @RequestParam(required = false) String trackingNumber
    ) {
        return ResponseEntity.ok(shippingService.updateStatus(orderId, status, trackingNumber));
    }
}
