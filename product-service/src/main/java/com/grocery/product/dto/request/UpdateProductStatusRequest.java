package com.grocery.product.dto.request;

import com.grocery.product.enums.ProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(

        @Schema(description = "ACTIVE or INACTIVE", example = "INACTIVE")
        @NotNull
        ProductStatus status) {
}
