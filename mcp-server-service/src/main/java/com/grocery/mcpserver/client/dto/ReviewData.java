package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * A review as GET /api/v1/products/{id}/reviews answers it (only visible reviews are returned).
 * The reviewer's name is left out on purpose: the assistant does not need to repeat people's names.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewData(int rating, String comment, Instant createdAt) {
}
