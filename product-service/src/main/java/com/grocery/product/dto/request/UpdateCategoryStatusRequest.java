package com.grocery.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateCategoryStatusRequest(

        @Schema(description = "false = hide the category", example = "false")
        @NotNull
        Boolean active) {
}
