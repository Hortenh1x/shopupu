package com.example.shopupu.shipping.mapper;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.shipping.dto.ShipmentDto;
import com.example.shopupu.shipping.dto.ShippingAddressDto;
import com.example.shopupu.shipping.entity.Shipment;
import com.example.shopupu.shipping.entity.ShippingAddress;
import org.springframework.stereotype.Component;

@Component
public class ShippingMapper {

    public ShippingAddressDto toDto(ShippingAddress a) {
        if (a == null) return null;
        return new ShippingAddressDto(
                a.getId(),
                a.getFullName(),
                a.getLine1(),
                a.getLine2(),
                a.getCity(),
                a.getState(),
                a.getPostalCode(),
                a.getCountry()
        );
    }

    public ShipmentDto toDto(Shipment s, Order order) {
        if (s == null || order == null) return null;
        return new ShipmentDto(
                order.getId(),
                s.getMethod(),
                s.getStatus(),
                order.getStatus(),
                s.getCost(),
                s.getCurrency(),
                s.getTrackingNumber(),
                toDto(s.getAddress()),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}

