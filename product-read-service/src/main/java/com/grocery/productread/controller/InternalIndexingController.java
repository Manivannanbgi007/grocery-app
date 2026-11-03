package com.grocery.productread.controller;

import com.grocery.productread.dto.common.ApiResponse;
import com.grocery.productread.messaging.consumer.CatalogListenerControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * For scripts/reindex-products.ps1: pause the catalog listener while an index is copied, then resume it.
 * /internal/** is never routed by the API gateway.
 */
@Tag(name = "Internal", description = "For other microservices only - not reachable through the API gateway")
@RestController
@RequestMapping("/internal/catalog/indexing")
@RequiredArgsConstructor
public class InternalIndexingController {

    private final CatalogListenerControl listenerControl;

    @Operation(summary = "Is the catalog listener running, is it paused?")
    @GetMapping
    public ResponseEntity<ApiResponse<CatalogListenerControl.Status>> status() {
        return ResponseEntity.ok(ApiResponse.success("Catalog listener status", listenerControl.status()));
    }

    @Operation(summary = "Pause the catalog listener (changes wait in Kafka); answers once it has paused")
    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<CatalogListenerControl.Status>> pause() throws InterruptedException {
        return ResponseEntity.ok(ApiResponse.success("Catalog listener paused", listenerControl.pause()));
    }

    @Operation(summary = "Resume the catalog listener: the changes that waited are applied")
    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<CatalogListenerControl.Status>> resume() {
        return ResponseEntity.ok(ApiResponse.success("Catalog listener resumed", listenerControl.resume()));
    }
}
