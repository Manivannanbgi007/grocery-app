package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The standard wrapper every grocery service answers with: { "success": ..., "message": ..., "data": ... }.
 * Only the fields this service needs; ignoreUnknown skips the others (errors, timestamp, path).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayResponse<T>(boolean success, String message, T data) {
}
