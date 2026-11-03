package com.grocery.gateway.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard response body - the same shape every grocery microservice returns.
 * The gateway only builds error responses itself (401, 403, 404, 503 ...);
 * successful responses come straight from the services.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<ErrorDetail> errors,
        Instant timestamp,
        String path) {

    public static ApiResponse<Void> error(String message, String path) {
        return new ApiResponse<>(false, message, null, null, Instant.now(), path);
    }

    /**
     * The gateway's graceful-degradation answer: the request worked, but with substitute data (for example an empty
     * banner list while platform-service is down). The message says so; the X-Fallback header marks it too.
     */
    public static <T> ApiResponse<T> fallback(String message, T data, String path) {
        return new ApiResponse<>(true, message, data, null, Instant.now(), path);
    }
}
