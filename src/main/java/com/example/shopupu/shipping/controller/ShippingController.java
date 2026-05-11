package com.example.shopupu.shipping.controller;

import com.example.shopupu.shipping.dto.ShipmentDto;
import com.example.shopupu.shipping.dto.SetShippingAddressRequest;
import com.example.shopupu.shipping.dto.SetShippingMethodRequest;
import com.example.shopupu.shipping.service.ShippingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
/**
 * describes the ShippingController class.
 */
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping("/address")
    // handles setAddress.
    public ResponseEntity<ShipmentDto> setAddress(@Valid @RequestBody SetShippingAddressRequest req) {
        return ResponseEntity.ok(shippingService.setAddress(req));
    }

    @PostMapping("/method")
    // handles setMethod.
    public ResponseEntity<ShipmentDto> setMethod(@Valid @RequestBody SetShippingMethodRequest req) {
        return ResponseEntity.ok(shippingService.setMethod(req));
    }

    @GetMapping("/{orderId}")
    // handles get.
    public ResponseEntity<ShipmentDto> get(@PathVariable Long orderId) {
        return ResponseEntity.ok(shippingService.getByOrder(orderId));
    }

}