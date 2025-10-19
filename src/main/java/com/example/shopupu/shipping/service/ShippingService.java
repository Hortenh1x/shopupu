package com.example.shopupu.shipping.service;

import com.example.shopupu.config.ShippingProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.shipping.dto.ShipmentDto;
import com.example.shopupu.shipping.dto.ShippingRequests;
import com.example.shopupu.shipping.entity.*;
import com.example.shopupu.shipping.mapper.ShippingMapper;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import com.example.shopupu.shipping.repository.ShippingAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShippingAddressRepository addressRepository;
    private final ShippingMapper mapper;
    private final ShippingProperties shippingProperties;
    private final UserService userService;

    @Transactional
    public ShipmentDto setAddress(ShippingRequests.SetAddress req) {
        Order order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        assertCanAccess(order);

        validateAddress(req);

        ShippingAddress address = ShippingAddress.builder()
                .fullName(req.fullName())
                .line1(req.line1())
                .line2(req.line2())
                .city(req.city())
                .state(req.state())
                .postalCode(req.postalCode())
                .country(req.country())
                .build();
        addressRepository.save(address);

        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElse(Shipment.builder()
                        .order(order)
                        .method(ShippingMethod.STANDARD_POST)
                        .status(ShippingStatus.PENDING)
                        .cost(rateFor(ShippingMethod.STANDARD_POST))
                        .currency(shippingProperties.getCurrency())
                        .build());

        shipment.setAddress(address);
        shipmentRepository.save(shipment);

        return mapper.toDto(shipment, order);
    }

    @Transactional
    public ShipmentDto setMethod(ShippingRequests.SetMethod req) {
        Order order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        assertCanAccess(order);

        if (req.method() == null) {
            throw new IllegalArgumentException("Shipping method is required");
        }

        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElse(Shipment.builder()
                        .order(order)
                        .status(ShippingStatus.PENDING)
                        .currency(shippingProperties.getCurrency())
                        .build());

        shipment.setMethod(req.method());
        shipment.setCost(rateFor(req.method()));
        shipmentRepository.save(shipment);
        return mapper.toDto(shipment, order);
    }

    @Transactional(readOnly = true)
    public ShipmentDto getByOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        assertCanAccess(order);
        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElse(null);
        if (shipment == null) {
            return new ShipmentDto(
                    order.getId(),
                    null,
                    null,
                    order.getStatus(),
                    null,
                    shippingProperties.getCurrency(),
                    null,
                    null,
                    null,
                    null
            );
        }
        return mapper.toDto(shipment, order);
    }

    @Transactional
    public ShipmentDto updateStatus(Long orderId, ShippingStatus newStatus, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        assertAdmin();
        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalArgumentException("Shipment not found for order"));
        shipment.setStatus(newStatus);
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            shipment.setTrackingNumber(trackingNumber);
        }
        shipmentRepository.save(shipment);
        return mapper.toDto(shipment, order);
    }

    private BigDecimal rateFor(ShippingMethod method) {
        Map<ShippingMethod, BigDecimal> rates = new EnumMap<>(ShippingMethod.class);
        rates.put(ShippingMethod.DHL, shippingProperties.getRates().getDhl());
        rates.put(ShippingMethod.STANDARD_POST, shippingProperties.getRates().getStandardPost());
        rates.put(ShippingMethod.LOCAL_PICKUP, shippingProperties.getRates().getLocalPickup());
        return rates.getOrDefault(method, shippingProperties.getRates().getDefaultRate());
    }

    private void validateAddress(ShippingRequests.SetAddress req) {
        requireNotBlank(req.fullName(), "fullName");
        requireNotBlank(req.line1(), "line1");
        requireNotBlank(req.city(), "city");
        requireNotBlank(req.state(), "state");
        requireNotBlank(req.postalCode(), "postalCode");
        requireNotBlank(req.country(), "country");
        // Simple length checks
        maxLen(req.fullName(), 128, "fullName");
        maxLen(req.line1(), 128, "line1");
        maxLen(nullToEmpty(req.line2()), 128, "line2");
        maxLen(req.city(), 64, "city");
        maxLen(req.state(), 64, "state");
        maxLen(req.postalCode(), 16, "postalCode");
        maxLen(req.country(), 64, "country");
    }

    private void requireNotBlank(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Field '" + field + "' is required");
        }
    }

    private void maxLen(String v, int max, String field) {
        if (v != null && v.length() > max) {
            throw new IllegalArgumentException("Field '" + field + "' max length is " + max);
        }
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    private void assertCanAccess(Order order) {
        String email = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        User current = userService.getByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
        Long ownerId = order.getUser().getId();
        boolean isOwner = ownerId != null && ownerId.equals(current.getId());
        boolean isAdmin = current.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
        if (!(isOwner || isAdmin)) {
            throw new SecurityException("Access denied to this order");
        }
    }

    private void assertAdmin() {
        String email = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        User current = userService.getByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
        boolean isAdmin = current.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
        if (!isAdmin) {
            throw new SecurityException("Admin privileges required");
        }
    }
}
