package com.archit.inventory.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryOutOfStockEvent(
        UUID productId,
        String sku,
        Instant occurredAt
) {
}
