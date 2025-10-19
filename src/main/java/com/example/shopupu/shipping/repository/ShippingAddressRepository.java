package com.example.shopupu.shipping.repository;

import com.example.shopupu.shipping.entity.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, Long> {
}

