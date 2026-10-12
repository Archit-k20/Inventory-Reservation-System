package com.archit.inventory.demo;

import java.util.UUID;

public record DemoOrderFailureResponse(
        UUID reservationId,
        String event,
        String reason,
        boolean published,
        boolean queued,
        UUID eventId
) {
}
