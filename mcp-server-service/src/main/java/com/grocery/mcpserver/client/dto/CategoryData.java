package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A category as GET /api/v1/categories answers it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryData(Long id, String name, String slug, String description) {
}
