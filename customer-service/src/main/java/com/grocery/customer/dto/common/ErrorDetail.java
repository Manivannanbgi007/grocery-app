package com.grocery.customer.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One error inside {@link ApiResponse#errors()}.
 *
 * @param field   the request field that is wrong (null when the error is not about a single field)
 * @param message what is wrong with it
 */
@Schema(description = "A single validation or business error")
public record ErrorDetail(String field, String message) {
}
