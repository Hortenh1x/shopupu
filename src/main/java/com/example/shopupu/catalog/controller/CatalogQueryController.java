package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.entity.Gender;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.service.ProductQueryService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog")
public class CatalogQueryController {

    private final ProductQueryService productQueryService;
    private final CatalogMapper catalogMapper;

    @GetMapping("/products/search")
    public Page<ProductListItem> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Gender gender,
            // named variantSize because a `size` request param is also consumed
            // by the Pageable resolver as the page size
            @RequestParam(required = false) String variantSize,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        var filter = new ProductFilter();
        filter.q = q;
        filter.categoryId = categoryId;
        filter.brandId = brandId;
        filter.gender = gender;
        filter.size = variantSize;
        filter.color = color;
        filter.minPrice = minPrice;
        filter.maxPrice = maxPrice;
        filter.inStock = inStock;
        // the public search never exposes disabled products
        filter.enabled = Boolean.TRUE;

        return productQueryService.findProducts(filter, pageable);
    }
}
