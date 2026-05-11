package com.example.shopupu.shipping.controller;

import com.example.shopupu.shipping.dto.ShipmentDto;
import com.example.shopupu.shipping.entity.ShippingStatus;
import com.example.shopupu.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/shipping")
@PreAuthorize("hasRole('ADMIN')")
/**
 * describes the AdminShippingController class.
 */
public class AdminShippingController {

    private final ShippingService shippingService;

    @PatchMapping("/{orderId}/status")
    // handles updateStatus.
    public ResponseEntity<ShipmentDto> updateStatus(
            @PathVariable Long orderId,
            @RequestParam ShippingStatus status,
            @RequestParam(required = false) String trackingNumber
    ) {
        return ResponseEntity.ok(shippingService.updateStatus(orderId, status, trackingNumber));
    }
}