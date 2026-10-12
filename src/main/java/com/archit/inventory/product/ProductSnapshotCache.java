package com.archit.inventory.product;

import com.archit.inventory.common.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProductSnapshotCache {
    private final ProductRepository products;

    public ProductSnapshotCache(ProductRepository products) {
        this.products = products;
    }

    // PostgreSQL's committed version selects the cache key. Old snapshots cannot
    // become current again after Redis recovers or a concurrent reader fills a key.
    @Cacheable(value = "product-snapshots-v2", key = "#id.toString() + ':' + #version")
    public ProductResponse get(UUID id, long version) {
        return products.findById(id).map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }
}
