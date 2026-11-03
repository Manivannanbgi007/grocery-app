package com.grocery.inventory.service;

import com.grocery.inventory.dto.response.StockSnapshotResponse;

/**
 * Publishes the CURRENT stock of every product again as StockChanged events.
 *
 * Needed when a consumer has to (re)build its copy of the stock, for example:
 * - the seed stock rows were inserted by SQL, so no event was ever published for them
 * - product-read-service's Elasticsearch index was deleted and rebuilt
 */
public interface StockSnapshotService {

    StockSnapshotResponse publishSnapshot();
}
