package com.example.shopupu.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Gender;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Catalog filtering against a real database: who the gender filter lets through,
 * and how a vector-search candidate set is narrowed without losing its ranking.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CatalogSearchIT extends PostgresContainerSupport {

    @Autowired
    private ProductQueryService productQueryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Product mensShirt;
    private Product womensDress;
    private Product unisexHoodie;
    private Product retiredHoodie;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        Category category = categoryRepository.save(
                new Category("Clothing", "clothing-" + System.nanoTime(), null, null));
        mensShirt = save("Linen Resort Shirt", category, Gender.MEN, true);
        womensDress = save("Wrap Jersey Dress", category, Gender.WOMEN, true);
        unisexHoodie = save("Oversized Cotton Hoodie", category, Gender.UNISEX, true);
        retiredHoodie = save("Retired Hoodie", category, Gender.UNISEX, false);
    }

    @Test
    void shoppingForMenIncludesUnisexButNotWomenswear() {
        List<Long> ids = idsMatching(Gender.MEN);

        assertTrue(ids.contains(mensShirt.getId()));
        assertTrue(ids.contains(unisexHoodie.getId()), "a unisex hoodie is also menswear");
        assertFalse(ids.contains(womensDress.getId()));
    }

    @Test
    void shoppingForWomenIncludesUnisexButNotMenswear() {
        List<Long> ids = idsMatching(Gender.WOMEN);

        assertTrue(ids.contains(womensDress.getId()));
        assertTrue(ids.contains(unisexHoodie.getId()));
        assertFalse(ids.contains(mensShirt.getId()));
    }

    @Test
    void pickingUnisexShowsUnisexOnly() {
        assertEquals(List.of(unisexHoodie.getId()), idsMatching(Gender.UNISEX));
    }

    @Test
    void candidateSetKeepsRelevanceOrderAndStillObeysFilters() {
        // the order a vector search would hand over, including an unsellable product
        List<Long> ranked = List.of(unisexHoodie.getId(), retiredHoodie.getId(),
                womensDress.getId(), mensShirt.getId());
        ProductFilter filter = new ProductFilter();
        filter.enabled = Boolean.TRUE;
        filter.gender = Gender.MEN;

        List<ProductListItem> items = productQueryService.findListItemsByIdsMatching(ranked, filter);

        // ranking preserved, womenswear filtered out, disabled product never surfaces
        assertEquals(List.of(unisexHoodie.getId(), mensShirt.getId()),
                items.stream().map(ProductListItem::id).toList());
    }

    private List<Long> idsMatching(Gender gender) {
        ProductFilter filter = new ProductFilter();
        filter.enabled = Boolean.TRUE;
        filter.gender = gender;
        return productQueryService.findProducts(filter, PageRequest.of(0, 50))
                .map(ProductListItem::id)
                .getContent();
    }

    private Product save(String title, Category category, Gender gender, boolean enabled) {
        Product product = new Product(title, title.toLowerCase().replace(' ', '-') + "-" + System.nanoTime(),
                title, new BigDecimal("10.00"), category);
        product.setGender(gender);
        product.setEnabled(enabled);
        return productRepository.save(product);
    }
}
