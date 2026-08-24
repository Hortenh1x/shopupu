package com.example.shopupu.orders.mapper;

import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.dto.OrderItemDto;
import com.example.shopupu.orders.dto.OrderStatusHistoryDto;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderItem;
import com.example.shopupu.orders.entity.OrderStatusHistory;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {

    OrderDto toDto(Order order);

    OrderItemDto toDto(OrderItem item);

    OrderStatusHistoryDto toDto(OrderStatusHistory history);
}
