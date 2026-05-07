package com.archit.inventory.product;

import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        long totalStock,
        long availableStock,
        long reservedStock,
        long soldStock,
        long version
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getTotalStock(),
                product.getAvailableStock(),
                product.getReservedStock(),
                product.getSoldStock(),
                product.getVersion()
        );
    }
}
