package com.example.shopupu.shipping.mapper;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.shipping.dto.ShipmentDto;
import com.example.shopupu.shipping.dto.ShippingAddressDto;
import com.example.shopupu.shipping.entity.Shipment;
import com.example.shopupu.shipping.entity.ShippingAddress;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ShippingMapper {

    ShippingAddressDto toDto(ShippingAddress address);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderStatus", source = "order.status")
    @Mapping(target = "method", source = "shipment.method")
    @Mapping(target = "shippingStatus", source = "shipment.status")
    @Mapping(target = "shippingCost", source = "shipment.cost")
    @Mapping(target = "currency", source = "shipment.currency")
    @Mapping(target = "trackingNumber", source = "shipment.trackingNumber")
    @Mapping(target = "address", source = "shipment.address")
    @Mapping(target = "createdAt", source = "shipment.createdAt")
    @Mapping(target = "updatedAt", source = "shipment.updatedAt")
    ShipmentDto toDto(Shipment shipment, Order order);
}
