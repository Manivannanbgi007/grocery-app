package com.grocery.mcpserver.tool.result;

import com.grocery.mcpserver.client.dto.StoreData;
import com.grocery.mcpserver.tool.Formats;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A store of the platform (listStores).
 *
 * @param status       "Open now", "Closed now" or "Not taking orders at the moment"
 * @param openingHours e.g. ["Monday-Saturday: 08:00-21:00", "Sunday: 09:00-14:00"] (India time)
 */
public record StoreInfo(String name, String description, String address, String phone, String status, List<String> openingHours) {

    public static StoreInfo from(StoreData store) {
        String address = Stream.of(store.addressLine1(), store.addressLine2(), store.city(), store.pincode())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
        String status;
        if (!store.acceptingOrders()) {
            status = "Not taking orders at the moment";
        } else {
            status = store.openNow() ? "Open now" : "Closed now";
        }
        return new StoreInfo(store.name(), store.description(), address, store.phone(), status, Formats.openingHours(store.openingHours()));
    }
}
