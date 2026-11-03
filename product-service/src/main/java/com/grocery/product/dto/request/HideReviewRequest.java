package com.grocery.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HideReviewRequest(@NotBlank @Size(max = 255) String reason) {
}
