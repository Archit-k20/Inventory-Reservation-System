package com.archit.inventory.order;

import com.archit.inventory.product.Product;
import com.archit.inventory.reservation.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class CustomerOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", referencedColumnName = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 128)
    private String userId;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CustomerOrder() {
    }

    public CustomerOrder(UUID orderId, Reservation reservation) {
        this.orderId = orderId;
        this.reservation = reservation;
        this.product = reservation.getProduct();
        this.userId = reservation.getUserId();
        this.quantity = reservation.getQuantity();
        this.createdAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public Product getProduct() {
        return product;
    }

    public String getUserId() {
        return userId;
    }

    public long getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
