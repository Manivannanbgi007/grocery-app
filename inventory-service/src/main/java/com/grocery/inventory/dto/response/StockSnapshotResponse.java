package com.grocery.inventory.dto.response;

/**
 * @param publishedEvents number of StockChanged events written to the outbox
 */
public record StockSnapshotResponse(int publishedEvents) {
}
