package com.grocery.store.service;

import com.grocery.store.dto.common.PageResponse;
import com.grocery.store.dto.response.PublicStoreResponse;
import com.grocery.store.dto.response.StoreResponse;
import org.springframework.data.domain.Pageable;

/**
 * Read-only store lookups for customers (public) and for other services (internal).
 */
public interface StoreQueryService {

    /** ACTIVE stores only. city and pincode are optional filters. */
    PageResponse<PublicStoreResponse> listActiveStores(String city, String pincode, Pageable pageable);

    /** An ACTIVE store; other statuses are reported as not found. */
    PublicStoreResponse getActiveStore(Long storeId);

    /** Any store, any status - for other services (e.g. order-service checks the store is open). */
    StoreResponse getStoreForService(Long storeId);
}
