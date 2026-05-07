package com.archit.inventory.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long totalStock;

    @Column(nullable = false)
    private long availableStock;

    @Column(nullable = false)
    private long reservedStock;

    @Column(nullable = false)
    private long soldStock;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(UUID id, String sku, String name, long totalStock) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.totalStock = totalStock;
        this.availableStock = totalStock;
        this.reservedStock = 0;
        this.soldStock = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void reserve(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (availableStock < quantity) {
            throw new IllegalStateException("insufficient stock");
        }
        availableStock -= quantity;
        reservedStock += quantity;
        touch();
    }

    public void confirm(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (reservedStock < quantity) {
            throw new IllegalStateException("insufficient reserved stock");
        }
        reservedStock -= quantity;
        soldStock += quantity;
        touch();
    }

    public void release(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (reservedStock < quantity) {
            throw new IllegalStateException("insufficient reserved stock");
        }
        reservedStock -= quantity;
        availableStock += quantity;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public long getTotalStock() {
        return totalStock;
    }

    public long getAvailableStock() {
        return availableStock;
    }

    public long getReservedStock() {
        return reservedStock;
    }

    public long getSoldStock() {
        return soldStock;
    }

    public long getVersion() {
        return version;
    }
}
