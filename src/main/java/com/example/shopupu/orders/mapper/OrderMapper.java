package com.example.shopupu.orders.mapper;

import com.example.shopupu.orders.dto.OrderDto;
import com.example.shopupu.orders.dto.OrderItemDto;
import com.example.shopupu.orders.dto.OrderStatusHistoryDto;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderItem;
import com.example.shopupu.orders.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.stereotype.Component;


@Component
public class OrderMapper {

    public OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toDto)
                .toList();

        return new OrderDto(
                order.getId(),
                order.getOrderNumber(),
                order.getSubtotalAmount(),
                order.getShippingAmount(),
                order.getDiscountAmount(),
                order.getPromoCode(),
                order.getPaymentAmount(),
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
                item.getVariantId(),
                item.getTitle(),
                item.getSku(),
                item.getSize(),
                item.getColor(),
                item.getBrand(),
                item.getPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }

    public OrderStatusHistoryDto toDto(OrderStatusHistory history) {
        return new OrderStatusHistoryDto(
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedBy(),
                history.getCreatedAt()
        );
    }
}
