package com.example.shopupu.catalog.model;

public class ProductFilter {
    public String q;          // поиск по названию/описанию/sku
    public Long categoryId;
    public Double minPrice;
    public Double maxPrice;
    public Boolean enabled;


    public ProductFilter() {}
}
