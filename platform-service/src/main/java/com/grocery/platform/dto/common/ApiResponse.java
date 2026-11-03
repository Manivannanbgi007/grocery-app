package com.grocery.platform.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Standard response body returned by EVERY endpoint of EVERY grocery microservice.
 *
 * <pre>
 * Success: { "success": true,  "message": "...", "data": {...}, "errors": null, "timestamp": "...", "path": null }
 * Error:   { "success": false, "message": "...", "data": null,  "errors": [...], "timestamp": "...", "path": "/api/v1/..." }
 * </pre>
 *
 * Controllers build it with {@link #success}; GlobalExceptionHandler builds it with {@link #error}.
 *
 * @param <T> type of the data payload
 */
@Schema(description = "Standard response wrapper used by every grocery microservice")
public record ApiResponse<T>(
        @Schema(description = "true when the request succeeded") boolean success,
        @Schema(description = "Human-readable result message") String message,
        @Schema(description = "Response payload, null on errors") T data,
        @Schema(description = "Validation or business errors, null on success") List<ErrorDetail> errors,
        @Schema(description = "When the response was created (UTC)") Instant timestamp,
        @Schema(description = "Request path, filled on errors") String path) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now(), null);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now(), null);
    }

    public static ApiResponse<Void> error(String message, List<ErrorDetail> errors, String path) {
        return new ApiResponse<>(false, message, null, errors, Instant.now(), path);
    }
}
