package com.archit.inventory.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedEvent(
        UUID reservationId,
        UUID productId,
        String userId,
        long quantity,
        Instant createdAt
) {
}
