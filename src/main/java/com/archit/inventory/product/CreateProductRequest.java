package com.archit.inventory.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        @Min(0) long totalStock
) {
}
