package com.example.shopupu.catalog.model;

import com.example.shopupu.catalog.entity.Gender;
import java.math.BigDecimal;

/** Internal search filter for the catalog (CAT-05). */
public class ProductFilter {
    public String q;
    public Long categoryId;
    public Long brandId;
    public Gender gender;
    public String size;
    public String color;
    public BigDecimal minPrice;
    public BigDecimal maxPrice;
    public Boolean inStock;
    public Boolean enabled;

    public ProductFilter() {}
}
