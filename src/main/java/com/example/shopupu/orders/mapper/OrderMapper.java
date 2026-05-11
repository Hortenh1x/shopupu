package com.example.shopupu.orders.mapper;

import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.dto.OrderItemDto;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
/**
 * describes the OrderMapper class.
 */
public class OrderMapper {

    // handles toDto.
    public OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toDto)
                .toList();

        return new OrderDto(
                order.getId(),
                order.getSubtotalAmount(),
                order.getShippingAmount(),
                order.getPaymentAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                itemDtos
        );
    }

    // handles toDto.
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