package com.example.shopupu.catalog.service;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * describes the ProductQueryServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductQueryService productQueryService;

    // handles findProducts.
    @Test
    void findProductsDelegatesToRepositoryWithSpecification() {
        Product product = new Product();
        ProductFilter filter = new ProductFilter();
        filter.q = "phone";
        filter.enabled = true;
        PageRequest pageable = PageRequest.of(0, 10);
        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(product)));

        var page = productQueryService.findProducts(filter, pageable);

        assertEquals(1, page.getTotalElements());
    }
}
