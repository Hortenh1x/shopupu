package com.example.shopupu.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.service.ProductQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Request-binding tests for the public catalog search (CAT-05).
 *
 * The clothing-size filter must ride on `variantSize`: a param literally named
 * `size` is also consumed by the Pageable resolver as the page size, so the two
 * meanings would collide on one query param.
 */
@ExtendWith(MockitoExtension.class)
class CatalogQueryControllerTest {

    @Mock
    private ProductQueryService productQueryService;

    @Captor
    private ArgumentCaptor<ProductFilter> filterCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new CatalogQueryController(productQueryService, new CatalogMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void variantSizeParamFiltersWhileSizeParamControlsPageSize() throws Exception {
        when(productQueryService.findProducts(any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/catalog/products/search")
                        .param("variantSize", "M")
                        .param("size", "12"))
                .andExpect(status().isOk());

        verify(productQueryService).findProducts(filterCaptor.capture(), pageableCaptor.capture());
        assertEquals("M", filterCaptor.getValue().size);
        assertEquals(12, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void numericPageSizeIsNotMisreadAsClothingSizeFilter() throws Exception {
        when(productQueryService.findProducts(any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/catalog/products/search").param("size", "12"))
                .andExpect(status().isOk());

        verify(productQueryService).findProducts(filterCaptor.capture(), pageableCaptor.capture());
        assertNull(filterCaptor.getValue().size, "page size must not leak into the clothing-size filter");
        assertEquals(12, pageableCaptor.getValue().getPageSize());
    }

    // an unpaged PageImpl is not JSON-serializable without Spring Data's web module
    private Page<ProductListItem> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
    }
}
