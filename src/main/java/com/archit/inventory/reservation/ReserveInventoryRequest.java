package com.archit.inventory.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReserveInventoryRequest(
        @NotNull UUID productId,
        @NotBlank String userId,
        @Positive long quantity
) {
}
