package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.StoreDetailsData;

/**
 * What getVendorStoreToday returns: the vendor's store right now and the work waiting in it.
 * The counts are left out (null) for a store that is not approved: it gets no orders yet.
 *
 * @param approval         "Approved", "Waiting for an admin's approval", "Rejected by an admin" ...
 * @param approvalNote     why an admin rejected or disabled the store
 * @param takingOrders     the vendor's own switch in the panel ("Taking new orders")
 * @param newOrders        orders waiting for the store to accept or reject them
 * @param acceptedOrders   accepted, still to be packed
 * @param packedOrders     packed, still to be marked ready for pickup
 * @param readyForPickup   waiting for a delivery partner to pick them up
 * @param lowStockProducts products at or below their low-stock level, sold out ones included
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoreToday(String store, String approval, String approvalNote, boolean openNow, boolean takingOrders,
                         Long newOrders, Long acceptedOrders, Long packedOrders, Long readyForPickup,
                         Long lowStockProducts) {

    public static StoreToday from(StoreDetailsData store, long newOrders, long acceptedOrders, long packedOrders,
                                  long readyForPickup, long lowStockProducts) {
        return new StoreToday(store.name(), PanelTexts.storeStatus(store.status()), store.statusReason(), store.openNow(),
                store.acceptingOrders(), newOrders, acceptedOrders, packedOrders, readyForPickup, lowStockProducts);
    }

    public static StoreToday notApproved(StoreDetailsData store) {
        return new StoreToday(store.name(), PanelTexts.storeStatus(store.status()), store.statusReason(), store.openNow(),
                store.acceptingOrders(), null, null, null, null, null);
    }
}
