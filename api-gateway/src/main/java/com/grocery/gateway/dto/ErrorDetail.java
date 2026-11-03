package com.grocery.gateway.dto;

/**
 * One error inside {@link ApiResponse#errors()} (same as in every service).
 */
public record ErrorDetail(String field, String message) {
}
