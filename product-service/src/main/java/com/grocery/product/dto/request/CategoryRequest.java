package com.grocery.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @Schema(example = "Frozen Foods")
        @NotBlank @Size(max = 60)
        String name,

        @Schema(example = "Frozen snacks, vegetables and desserts")
        @Size(max = 255)
        String description,

        @Schema(example = "https://example.com/images/frozen.png")
        @Size(max = 500)
        String imageUrl,

        @Schema(description = "Position on the home page, lower = first (optional, default 0)", example = "6")
        @Min(0)
        Integer displayOrder) {
}
