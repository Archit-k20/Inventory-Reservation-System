package com.archit.inventory.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String key;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected IdempotencyKey() {
    }

    public void attachReservation(UUID reservationId) {
        this.reservationId = reservationId;
        this.updatedAt = Instant.now();
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getReservationId() {
        return reservationId;
    }
}
