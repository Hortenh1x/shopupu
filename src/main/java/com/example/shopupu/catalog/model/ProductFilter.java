package com.example.shopupu.catalog.model;

/**
 * describes the ProductFilter class.
 */
public class ProductFilter {
    public String q;
    public Long categoryId;
    public Double minPrice;
    public Double maxPrice;
    public Boolean enabled;


    // handles ProductFilter.
    public ProductFilter() {}
}