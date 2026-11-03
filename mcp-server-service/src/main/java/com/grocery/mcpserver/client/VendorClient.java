package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.DailySalesData;
import com.grocery.mcpserver.client.dto.GatewayResponse;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.StockProductData;
import com.grocery.mcpserver.client.dto.StoreDetailsData;
import com.grocery.mcpserver.client.dto.TopProductData;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The logged-in vendor's own store through the API gateway: the store (store-service), its orders (order-service),
 * its products with their stock (product-read-service) and its sales (report-service).
 *
 * Every call carries the vendor's token (Authorization: Bearer ...). The services take the store from the token, so
 * these calls can only return that vendor's own store, exactly like the vendor panel. Nothing is cached.
 */
@Component
@RequiredArgsConstructor
public class VendorClient {

    private static final ParameterizedTypeReference<GatewayResponse<StoreDetailsData>> STORE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<OrderSummaryData>>> ORDER_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<StockProductData>>> PRODUCT_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<DailySalesData>> DAILY_SALES = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<List<TopProductData>>> TOP_PRODUCTS = new ParameterizedTypeReference<>() { };

    private final RestClient gatewayRestClient;

    /** GET /api/v1/vendor/store; empty when the vendor has not registered a store yet (404). */
    public Optional<StoreDetailsData> store(String vendorToken) {
        return GatewayCall.optionalDataOf("The store", () -> gatewayRestClient.get()
                .uri("/api/v1/vendor/store")
                .headers(headers -> headers.setBearerAuth(vendorToken))
                .retrieve()
                .body(STORE));
    }

    /**
     * GET /api/v1/vendor/orders: the newest orders first.
     *
     * @param status an order status such as CONFIRMED (the new orders), or null for every status. With size 1 the
     *               answer is mainly the count (totalElements).
     */
    public PageData<OrderSummaryData> orders(String vendorToken, String status, int size) {
        return GatewayCall.dataOf("The store's orders", () -> gatewayRestClient.get()
                .uri(uri -> {
                    uri.path("/api/v1/vendor/orders").queryParam("page", 0).queryParam("size", size);
                    if (status != null) {
                        uri.queryParam("status", status);
                    }
                    return uri.build();
                })
                .headers(headers -> headers.setBearerAuth(vendorToken))
                .retrieve()
                .body(ORDER_PAGE));
    }

    /** GET /api/v1/vendor/products?lowStockOnly=true: the products at or below their low-stock level, sold out included. */
    public PageData<StockProductData> lowStockProducts(String vendorToken, int size) {
        return GatewayCall.dataOf("The store's products", () -> gatewayRestClient.get()
                .uri("/api/v1/vendor/products?lowStockOnly=true&page=0&size={size}", size)
                .headers(headers -> headers.setBearerAuth(vendorToken))
                .retrieve()
                .body(PRODUCT_PAGE));
    }

    /** GET /api/v1/vendor/reports/sales/daily: the store's sales per day, both dates included (India dates). */
    public DailySalesData dailySales(String vendorToken, LocalDate from, LocalDate to) {
        return GatewayCall.dataOf("The store's sales", () -> gatewayRestClient.get()
                .uri("/api/v1/vendor/reports/sales/daily?from={from}&to={to}", from, to)
                .headers(headers -> headers.setBearerAuth(vendorToken))
                .retrieve()
                .body(DAILY_SALES));
    }

    /** GET /api/v1/vendor/reports/top-products: the store's best sellers of the period, best first. */
    public List<TopProductData> topProducts(String vendorToken, LocalDate from, LocalDate to, int limit) {
        return GatewayCall.dataOf("The store's sales", () -> gatewayRestClient.get()
                .uri("/api/v1/vendor/reports/top-products?from={from}&to={to}&limit={limit}", from, to, limit)
                .headers(headers -> headers.setBearerAuth(vendorToken))
                .retrieve()
                .body(TOP_PRODUCTS));
    }
}
