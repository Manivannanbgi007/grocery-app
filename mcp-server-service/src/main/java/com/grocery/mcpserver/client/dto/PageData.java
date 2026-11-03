package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One page of a list (PageResponse in the services). Page numbers start at 0.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageData<T>(List<T> content, long totalElements) {
}
