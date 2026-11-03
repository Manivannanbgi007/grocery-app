package com.grocery.store.service;

import com.grocery.store.dto.request.StoreDetailsRequest;
import com.grocery.store.dto.request.UpdateAcceptingOrdersRequest;
import com.grocery.store.dto.request.UpdateDeliveryAreasRequest;
import com.grocery.store.dto.request.UpdateOpeningHoursRequest;
import com.grocery.store.dto.response.StoreResponse;

/**
 * Store panel: a vendor manages their own store. The vendor is identified by their user id
 * (X-User-Id), because the JWT only contains a storeId after the store has been approved.
 */
public interface VendorStoreService {

    /** Registers the vendor's store. It starts as PENDING_APPROVAL with default hours and delivery area. */
    StoreResponse registerStore(Long ownerUserId, StoreDetailsRequest request);

    StoreResponse getMyStore(Long ownerUserId);

    /** Editing a REJECTED store sends it back for approval. */
    StoreResponse updateMyStore(Long ownerUserId, StoreDetailsRequest request);

    StoreResponse updateOpeningHours(Long ownerUserId, UpdateOpeningHoursRequest request);

    StoreResponse updateDeliveryAreas(Long ownerUserId, UpdateDeliveryAreasRequest request);

    StoreResponse updateAcceptingOrders(Long ownerUserId, UpdateAcceptingOrdersRequest request);
}
