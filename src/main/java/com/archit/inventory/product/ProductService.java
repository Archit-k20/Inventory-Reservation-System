package com.archit.inventory.product;

import com.archit.inventory.common.ConflictException;
import com.archit.inventory.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductSnapshotCache snapshots;

    public ProductService(ProductRepository productRepository, ProductSnapshotCache snapshots) {
        this.productRepository = productRepository;
        this.snapshots = snapshots;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("SKU already exists");
        }
        Product product = new Product(UUID.randomUUID(), request.sku(), request.name(), request.totalStock());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        long version = productRepository.findVersionById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        return snapshots.get(id, version);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }
}
