package com.archit.inventory.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReserveInventoryRequest(
        @NotNull UUID productId,
        @NotBlank @Size(max = 128) String userId,
        @Positive long quantity
) {
}
