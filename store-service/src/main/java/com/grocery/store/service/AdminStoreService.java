package com.grocery.store.service;

import com.grocery.store.dto.common.PageResponse;
import com.grocery.store.dto.request.ReasonRequest;
import com.grocery.store.dto.request.UpdateCommissionRequest;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.enums.StoreStatus;
import org.springframework.data.domain.Pageable;

/**
 * Admin and Super Admin panels: approve, reject, disable and enable stores, set commission.
 */
public interface AdminStoreService {

    /** Every filter is optional (null = no filter). */
    PageResponse<StoreResponse> searchStores(StoreStatus status, String city, String keyword, Pageable pageable);

    StoreResponse getStore(Long storeId);

    /** PENDING_APPROVAL -> ACTIVE. Publishes StoreApproved. */
    StoreResponse approveStore(Long storeId, Long adminUserId);

    /** PENDING_APPROVAL -> REJECTED. Publishes StoreStatusChanged. */
    StoreResponse rejectStore(Long storeId, ReasonRequest request);

    /** ACTIVE -> DISABLED. Publishes StoreStatusChanged. */
    StoreResponse disableStore(Long storeId, ReasonRequest request);

    /** DISABLED -> ACTIVE. Publishes StoreStatusChanged. */
    StoreResponse enableStore(Long storeId);

    /** Super admin only. */
    StoreResponse updateCommission(Long storeId, UpdateCommissionRequest request);
}
