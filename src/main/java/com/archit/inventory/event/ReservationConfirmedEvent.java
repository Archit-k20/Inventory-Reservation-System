package com.archit.inventory.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationConfirmedEvent(
        UUID reservationId,
        UUID orderId,
        UUID productId,
        Instant confirmedAt
) {
}
