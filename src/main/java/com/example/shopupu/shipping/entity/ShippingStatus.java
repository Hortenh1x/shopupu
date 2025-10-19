package com.example.shopupu.shipping.entity;

/**
 * EN: Shipment lifecycle statuses.
 */
public enum ShippingStatus {
    PENDING,
    PREPARING,
    SHIPPED,
    DELIVERED,
    READY_FOR_PICKUP,
    PICKED_UP,
    CANCELED
}

