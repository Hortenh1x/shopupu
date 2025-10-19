package com.example.shopupu.shipping.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * EN: Shipping address entity entered by the user.
 */
@Entity
@Table(name = "shipping_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String fullName;

    @Column(nullable = false, length = 128)
    private String line1;

    @Column(length = 128)
    private String line2;

    @Column(nullable = false, length = 64)
    private String city;

    @Column(nullable = false, length = 64)
    private String state;

    @Column(nullable = false, length = 16)
    private String postalCode;

    @Column(nullable = false, length = 64)
    private String country;
}

