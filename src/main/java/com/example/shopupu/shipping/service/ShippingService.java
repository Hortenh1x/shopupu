package com.example.shopupu.shipping.service;

import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.config.ShippingProperties;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.shipping.dto.SetShippingAddressRequest;
import com.example.shopupu.shipping.dto.SetShippingMethodRequest;
import com.example.shopupu.shipping.dto.ShipmentDto;
import com.example.shopupu.shipping.entity.*;
import com.example.shopupu.shipping.mapper.ShippingMapper;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import com.example.shopupu.shipping.repository.ShippingAddressRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShippingAddressRepository addressRepository;
    private final ShippingMapper mapper;
    private final ShippingProperties shippingProperties;
    private final AccessControlService accessControlService;
    private final OrderService orderService;

    @Transactional
    public ShipmentDto setAddress(SetShippingAddressRequest req) {
        Order order = findOrder(req.orderId());
        accessControlService.requireOrderOwnerOrAdmin(order);
        ensureOrderCanChangeShipping(order);
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

        Shipment shipment = findOrCreateShipment(order);
        ShippingAddress previous = shipment.getAddress();
        shipment.setAddress(address);
        shipment.setAddressSnapshot(snapshotOf(address));
        shipmentRepository.save(shipment);
        if (previous != null) {
            // replaced addresses are per-order rows; drop them instead of orphaning
            addressRepository.delete(previous);
        }

        orderService.updateShippingAmount(order.getId(), shipment.getCost());

        return mapper.toDto(shipment, order);
    }

    @Transactional
    public ShipmentDto setMethod(SetShippingMethodRequest req) {
        Order order = findOrder(req.orderId());
        accessControlService.requireOrderOwnerOrAdmin(order);
        ensureOrderCanChangeShipping(order);

        if (req.method() == null) {
            throw new BusinessRuleException("Shipping method is required");
        }

        Shipment shipment = findOrCreateShipment(order);
        shipment.setMethod(req.method());
        shipment.setCost(rateFor(req.method(), order.getSubtotalAmount()));
        shipmentRepository.save(shipment);

        orderService.updateShippingAmount(order.getId(), shipment.getCost());
        return mapper.toDto(shipment, order);
    }

    @Transactional(readOnly = true)
    public ShipmentDto getByOrder(Long orderId) {
        Order order = findOrder(orderId);
        accessControlService.requireOrderOwnerOrAdmin(order);

        Shipment shipment = shipmentRepository.findByOrder(order).orElse(null);
        if (shipment == null) {
            return emptyShipment(order);
        }
        return mapper.toDto(shipment, order);
    }

    @Transactional
    public ShipmentDto updateStatus(Long orderId, ShippingStatus newStatus, String trackingNumber) {
        Order order = findOrder(orderId);
        accessControlService.requireAdmin();

        Shipment shipment = shipmentRepository.findByOrder(order)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found for order"));
        shipment.setStatus(newStatus);
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            shipment.setTrackingNumber(trackingNumber);
        }
        shipmentRepository.save(shipment);
        return mapper.toDto(shipment, order);
    }

    /** Flat method rates with a free-shipping threshold on the order subtotal (SHIP-05). */
    private BigDecimal rateFor(ShippingMethod method, BigDecimal orderSubtotal) {
        if (orderSubtotal != null
                && shippingProperties.getFreeShippingThreshold() != null
                && shippingProperties.getFreeShippingThreshold().signum() > 0
                && orderSubtotal.compareTo(shippingProperties.getFreeShippingThreshold()) >= 0) {
            return BigDecimal.ZERO;
        }
        if (method == ShippingMethod.DHL) {
            return shippingProperties.getRates().getDhl();
        }
        if (method == ShippingMethod.STANDARD_POST) {
            return shippingProperties.getRates().getStandardPost();
        }
        if (method == ShippingMethod.LOCAL_PICKUP) {
            return shippingProperties.getRates().getLocalPickup();
        }
        return shippingProperties.getRates().getDefaultRate();
    }

    private String snapshotOf(ShippingAddress a) {
        StringBuilder sb = new StringBuilder();
        sb.append(a.getFullName()).append(", ").append(a.getLine1());
        if (a.getLine2() != null && !a.getLine2().isBlank()) {
            sb.append(", ").append(a.getLine2());
        }
        sb.append(", ").append(a.getCity())
                .append(", ").append(a.getState())
                .append(" ").append(a.getPostalCode())
                .append(", ").append(a.getCountry());
        return sb.toString();
    }

    private void validateAddress(SetShippingAddressRequest req) {
        requireNotBlank(req.fullName(), "fullName");
        requireNotBlank(req.line1(), "line1");
        requireNotBlank(req.city(), "city");
        requireNotBlank(req.state(), "state");
        requireNotBlank(req.postalCode(), "postalCode");
        requireNotBlank(req.country(), "country");

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
            throw new BadRequestException("Field '" + field + "' is required");
        }
    }

    private void maxLen(String v, int max, String field) {
        if (v != null && v.length() > max) {
            throw new BadRequestException("Field '" + field + "' max length is " + max);
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void ensureOrderCanChangeShipping(Order order) {
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessRuleException("Shipping can only be changed for orders awaiting payment setup");
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private Shipment findOrCreateShipment(Order order) {
        return shipmentRepository.findByOrder(order)
                .orElseGet(() -> Shipment.builder()
                        .order(order)
                        .method(ShippingMethod.STANDARD_POST)
                        .status(ShippingStatus.PENDING)
                        .cost(rateFor(ShippingMethod.STANDARD_POST, order.getSubtotalAmount()))
                        .currency(shippingProperties.getCurrency())
                        .build());
    }

    private ShipmentDto emptyShipment(Order order) {
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
}
