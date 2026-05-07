package com.archit.inventory.event;

import java.util.UUID;

public record OrderFailedEvent(
        UUID reservationId,
        String reason
) {
}
