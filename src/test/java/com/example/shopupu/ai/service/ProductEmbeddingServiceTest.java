package com.example.shopupu.ai.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.shopupu.ai.gateway.EmbeddingClient;
import com.example.shopupu.ai.repository.ProductEmbeddingRepository;
import com.example.shopupu.catalog.entity.Brand;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.config.AiProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * describes the ProductEmbeddingServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class ProductEmbeddingServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private ProductEmbeddingRepository embeddingRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AiProperties aiProperties;
    private ProductEmbeddingService service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        aiProperties.setEmbeddingDim(4);
        // the template just runs the callback: transaction boundaries are not under test
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<Object>>getArgument(0).doInTransaction(null));
        service = new ProductEmbeddingService(aiProperties, embeddingClient, embeddingRepository,
                productRepository, transactionTemplate, new SimpleMeterRegistry());
    }

    @Test
    void indexProductUpsertsEmbeddingForSellableProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sellableProduct()));
        when(embeddingClient.embedDocuments(any())).thenReturn(List.of(new float[] {1, 0, 0, 0}));

        service.indexProduct(1L);

        verify(embeddingRepository).upsert(eq(1L), eq(aiProperties.getEmbeddingModel()), any());
    }

    @Test
    void indexProductDeletesEmbeddingWhenProductIsNotSellable() {
        Product product = sellableProduct();
        product.setEnabled(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        service.indexProduct(1L);

        verify(embeddingRepository).deleteByProductId(1L);
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void indexProductIsNoOpWhenAiIsDisabled() {
        aiProperties.setEnabled(false);

        service.indexProduct(1L);

        verifyNoInteractions(embeddingClient, embeddingRepository, productRepository);
    }

    @Test
    void indexProductRejectsWrongEmbeddingDimension() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sellableProduct()));
        when(embeddingClient.embedDocuments(any())).thenReturn(List.of(new float[] {1, 0}));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.indexProduct(1L));
        verify(embeddingRepository, never()).upsert(anyLong(), any(), any());
    }

    @Test
    void embeddingTextContainsSearchableAttributes() {
        String text = ProductEmbeddingService.buildEmbeddingText(sellableProduct());

        assertTrue(text.contains("Oversized Hoodie"));
        assertTrue(text.contains("warm cotton hoodie"));
        assertTrue(text.contains("Acme"));
        assertTrue(text.contains("Hoodies"));
        assertTrue(text.contains("UNISEX"));
        assertTrue(text.contains("WINTER"));
    }

    private Product sellableProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setTitle("Oversized Hoodie");
        product.setDescription("warm cotton hoodie");
        product.setEnabled(true);
        product.setSeason("WINTER");
        Brand brand = new Brand("Acme", "acme");
        product.setBrand(brand);
        product.setCategory(new Category("Hoodies", "hoodies", null, null));
        return product;
    }
}
