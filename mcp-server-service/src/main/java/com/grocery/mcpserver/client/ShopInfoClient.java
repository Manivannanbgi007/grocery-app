package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.CouponData;
import com.grocery.mcpserver.client.dto.GatewayResponse;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.SettingsData;
import com.grocery.mcpserver.client.dto.StoreData;
import com.grocery.mcpserver.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Shop information through the API gateway: stores (store-service), coupons (cart-service) and the delivery rules
 * (platform-service). All of it rarely changes, so each answer is cached for a minute.
 */
@Component
@RequiredArgsConstructor
public class ShopInfoClient {

    private static final ParameterizedTypeReference<GatewayResponse<PageData<StoreData>>> STORE_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<List<CouponData>>> COUPONS = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<SettingsData>> SETTINGS = new ParameterizedTypeReference<>() { };

    /** More stores than the platform will have for a long time; one page is enough. */
    private static final int MAX_STORES = 100;

    private final RestClient gatewayRestClient;

    /** GET /api/v1/stores: the approved stores, sorted by name. */
    @Cacheable(CacheNames.STORES)
    public List<StoreData> stores() {
        PageData<StoreData> page = GatewayCall.dataOf("The store list", () -> gatewayRestClient.get()
                .uri("/api/v1/stores?size={size}", MAX_STORES)
                .retrieve()
                .body(STORE_PAGE));
        return page.content();
    }

    /** GET /api/v1/coupons: the coupons valid in every store (platform coupons). */
    @Cacheable(value = CacheNames.COUPONS, key = "'platform'")
    public List<CouponData> platformCoupons() {
        return GatewayCall.dataOf("The offers", () -> gatewayRestClient.get()
                .uri("/api/v1/coupons")
                .retrieve()
                .body(COUPONS));
    }

    /** GET /api/v1/coupons?storeId=: the platform coupons plus this store's own coupons. */
    @Cacheable(value = CacheNames.COUPONS, key = "#storeId")
    public List<CouponData> couponsOfStore(long storeId) {
        return GatewayCall.dataOf("The offers", () -> gatewayRestClient.get()
                .uri("/api/v1/coupons?storeId={storeId}", storeId)
                .retrieve()
                .body(COUPONS));
    }

    /** GET /api/v1/platform/settings: delivery fee, free-delivery amount, minimum order, support contact. */
    @Cacheable(CacheNames.SETTINGS)
    public SettingsData settings() {
        return GatewayCall.dataOf("The delivery rules", () -> gatewayRestClient.get()
                .uri("/api/v1/platform/settings")
                .retrieve()
                .body(SETTINGS));
    }
}
