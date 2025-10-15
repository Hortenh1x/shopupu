package com.example.shopupu.orders.mapper;

import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.dto.OrderItemDto;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RU: Маппер из сущностей в DTO.
 * EN: Mapper to convert entities to DTOs.
 */
@Component
public class OrderMapper {

    public OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toDto)
                .toList();

        return new OrderDto(
                order.getId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                itemDtos
        );
    }

    public OrderItemDto toDto(OrderItem item) {
        return new OrderItemDto(
                item.getId(),
                item.getProductId(),
                item.getTitle(),
                item.getPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
