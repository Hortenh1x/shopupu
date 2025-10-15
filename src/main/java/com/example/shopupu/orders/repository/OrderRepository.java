package com.example.shopupu.orders.repository;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE Order o SET o.status = :status WHERE o.id = :orderId")
    void updateStatus(@Param("orderId") Long orderId, @Param("status") OrderStatus status);

    // RU: Найти все заказы конкретного пользователя
    // EN: Find all orders for a given user
    List<Order> findByUser(User user);
}
