package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.StoreData;
import com.grocery.mcpserver.exception.ExternalServiceException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Store id -> name ("Fresh Mart"), for the cart tools' texts. Without the store list the texts just have no store names
 * (an empty map; look up only real store ids: Map.of() does not accept null).
 */
final class StoreNames {

    private StoreNames() {
    }

    static Map<Long, String> of(ShopInfoClient shopInfo) {
        try {
            return shopInfo.stores().stream().collect(Collectors.toMap(StoreData::id, StoreData::name));
        } catch (ExternalServiceException ex) {
            return Map.of();
        }
    }
}
