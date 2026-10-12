package com.archit.inventory.product;

import com.archit.inventory.common.ConflictException;
import com.archit.inventory.common.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceTest {
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductService productService = new ProductService(productRepository, new ProductSnapshotCache(productRepository));

    @Test
    void createRejectsDuplicateSku() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(new CreateProductRequest("SKU-1", "Widget", 10)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("SKU already exists");
    }

    @Test
    void getReturnsProduct() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findVersionById(productId)).thenReturn(Optional.of(0L));
        when(productRepository.findById(productId)).thenReturn(Optional.of(new Product(productId, "SKU-2", "Widget", 10)));

        ProductResponse response = productService.get(productId);

        assertThat(response.id()).isEqualTo(productId);
        assertThat(response.totalStock()).isEqualTo(10);
        assertThat(response.availableStock()).isEqualTo(10);
        assertThat(response.reservedStock()).isZero();
        assertThat(response.soldStock()).isZero();
    }

    @Test
    void getRejectsUnknownProduct() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findVersionById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(productId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product not found");
    }
}
