package com.example.shopupu.orders.repository;

import com.example.shopupu.orders.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * RU: Репозиторий для позиций заказов
 * EN: Repository for order items
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
