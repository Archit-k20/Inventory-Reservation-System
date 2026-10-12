package com.archit.inventory.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank @Size(max = 128) String sku,
        @NotBlank @Size(max = 255) String name,
        @Min(0) long totalStock
) {
}
