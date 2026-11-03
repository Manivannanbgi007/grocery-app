package com.grocery.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why an admin rejects or disables a store. Shown to the vendor.
 */
public record ReasonRequest(

        @Schema(example = "The store address could not be verified")
        @NotBlank @Size(max = 255)
        String reason) {
}
