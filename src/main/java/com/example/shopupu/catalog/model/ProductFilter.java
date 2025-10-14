package com.example.shopupu.catalog.model;

public class ProductFilter {
    public String q;          // поиск по названию/описанию/sku
    public Long categoryId;   // фильтр по категории
    public Double minPrice;   // цена от
    public Double maxPrice;   // цена до
    public Boolean enabled;   // только доступные/все

    // пустой конструктор нужен для удобной сборки
    public ProductFilter() {}
}
