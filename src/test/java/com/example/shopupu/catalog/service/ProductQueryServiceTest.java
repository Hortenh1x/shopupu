package com.example.shopupu.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

/**
 * describes the ProductQueryServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @org.mockito.Spy
    private com.example.shopupu.catalog.mapper.CatalogMapper catalogMapper = new com.example.shopupu.catalog.mapper.CatalogMapper();

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

    // handles findListItemsByIds.
    @Test
    void findListItemsByIdsKeepsRelevanceOrderAndDropsUnsellable() {
        Product first = sellableProduct(1L);
        Product second = sellableProduct(2L);
        Product deleted = sellableProduct(3L);
        deleted.setDeletedAt(java.time.Instant.now());
        // repository returns them in arbitrary order; ids define the relevance order
        when(productRepository.findAllById(List.of(2L, 3L, 1L)))
                .thenReturn(List.of(first, deleted, second));

        var items = productQueryService.findListItemsByIds(List.of(2L, 3L, 1L));

        assertEquals(List.of(2L, 1L), items.stream().map(i -> i.id()).toList());
    }

    @Test
    void findListItemsByIdsReturnsEmptyForEmptyInput() {
        assertEquals(List.of(), productQueryService.findListItemsByIds(List.of()));
    }

    private Product sellableProduct(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setTitle("p" + id);
        product.setEnabled(true);
        return product;
    }
}
