package com.grocery.auth.service;

import com.grocery.events.store.StoreApproved;

/**
 * Reacts to events published by store-service.
 */
public interface StoreEventService {

    /** Links the approved store to its vendor, so the vendor's next JWT contains storeId. */
    void handleStoreApproved(StoreApproved event);
}
