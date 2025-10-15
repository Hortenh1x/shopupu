package com.example.shopupu.orders.repository;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * RU: Репозиторий для заказов
 * EN: Repository for orders
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    // RU: Найти все заказы конкретного пользователя
    // EN: Find all orders for a given user
    List<Order> findByUser(User user);
}
