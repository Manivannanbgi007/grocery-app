package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.AdminDashboardData;
import com.grocery.mcpserver.client.dto.DailySalesData;
import com.grocery.mcpserver.client.dto.DeliveryData;
import com.grocery.mcpserver.client.dto.GatewayResponse;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.PartnerData;
import com.grocery.mcpserver.client.dto.StoreDetailsData;
import com.grocery.mcpserver.client.dto.TopProductData;
import com.grocery.mcpserver.client.dto.TopStoreData;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

/**
 * The admin panel's data through the API gateway: the dashboard and sales (report-service), stores (store-service),
 * delivery partners and deliveries (delivery-service), orders (order-service).
 *
 * Every call carries the admin's token; the gateway lets only ADMIN and SUPER_ADMIN logins through (403 otherwise),
 * exactly like the admin panel. Nothing is cached.
 */
@Component
@RequiredArgsConstructor
public class AdminClient {

    private static final ParameterizedTypeReference<GatewayResponse<AdminDashboardData>> DASHBOARD = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<StoreDetailsData>>> STORE_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<PartnerData>>> PARTNER_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<DeliveryData>>> DELIVERY_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<OrderSummaryData>>> ORDER_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<DailySalesData>> DAILY_SALES = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<List<TopStoreData>>> TOP_STORES = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<List<TopProductData>>> TOP_PRODUCTS = new ParameterizedTypeReference<>() { };

    private final RestClient gatewayRestClient;

    /** GET /api/v1/admin/reports/dashboard */
    public AdminDashboardData dashboard(String adminToken) {
        return GatewayCall.dataOf("The dashboard", () -> gatewayRestClient.get()
                .uri("/api/v1/admin/reports/dashboard")
                .headers(headers -> headers.setBearerAuth(adminToken))
                .retrieve()
                .body(DASHBOARD));
    }

    /** GET /api/v1/admin/stores?status=...: e.g. PENDING_APPROVAL for the stores waiting for an approval. */
    public PageData<StoreDetailsData> stores(String adminToken, String status, int size) {
        return GatewayCall.dataOf("The store list", () -> gatewayRestClient.get()
                .uri("/api/v1/admin/stores?status={status}&page=0&size={size}", status, size)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .retrieve()
                .body(STORE_PAGE));
    }

    /** GET /api/v1/admin/delivery-partners?kycStatus=...: e.g. PENDING for the KYC waiting for a review (oldest first). */
    public PageData<PartnerData> partners(String adminToken, String kycStatus, int size) {
        return GatewayCall.dataOf("The partner list", () -> gatewayRestClient.get()
                .uri("/api/v1/admin/delivery-partners?kycStatus={kycStatus}&page=0&size={size}", kycStatus, size)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .retrieve()
                .body(PARTNER_PAGE));
    }

    /** GET /api/v1/admin/deliveries?status=...: e.g. WAITING_FOR_PARTNER for the deliveries nobody took (newest first). */
    public PageData<DeliveryData> deliveries(String adminToken, String status, int size) {
        return GatewayCall.dataOf("The delivery list", () -> gatewayRestClient.get()
                .uri("/api/v1/admin/deliveries?status={status}&page=0&size={size}", status, size)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .retrieve()
                .body(DELIVERY_PAGE));
    }

    /**
     * GET /api/v1/admin/orders: every store's orders, newest first.
     *
     * @param status an order status such as CONFIRMED, or null for every status
     */
    public PageData<OrderSummaryData> orders(String adminToken, String status, int size) {
        return GatewayCall.dataOf("The order list", () -> gatewayRestClient.get()
                .uri(uri -> {
                    uri.path("/api/v1/admin/orders").queryParam("page", 0).queryParam("size", size);
                    if (status != null) {
                        uri.queryParam("status", status);
                    }
                    return uri.build();
                })
                .headers(headers -> headers.setBearerAuth(adminToken))
                .retrieve()
                .body(ORDER_PAGE));
    }

    /** GET /api/v1/admin/reports/sales/daily: the platform's sales per day, both dates included (India dates). */
    public DailySalesData dailySales(String adminToken, LocalDate from, LocalDate to) {
        return GatewayCall.dataOf("The sales report", () -> gatewayRestClient.get()
                .uri("/api/v1/admin/reports/sales/daily?from={from}&to={to}", from, to)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .retrieve()
                .body(DAILY_SALES));
    }

    /** GET /api/v1/admin/reports/top-stores: the stores with the highest sales of the period, best first. */
    public List<TopStoreData> topStores(String adminToken, LocalDate from, LocalDate to, int limit) {
        return GatewayCall.dataOf("The sales report", () -> gatewayRestClient.get()
                .uri("/api/v1/admin/reports/top-stores?from={from}&to={to}&limit={limit}", from, to, limit)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .retrieve()
                .body(TOP_STORES));
    }

    /** GET /api/v1/admin/reports/top-products: the platform's best sellers of the period, best first. */
    public List<TopProductData> topProducts(String adminToken, LocalDate from, LocalDate to, int limit) {
        return GatewayCall.dataOf("The sales report", () -> gatewayRestClient.get()
                .uri("/api/v1/admin/reports/top-products?from={from}&to={to}&limit={limit}", from, to, limit)
                .headers(headers -> headers.setBearerAuth(adminToken))
                .retrieve()
                .body(TOP_PRODUCTS));
    }
}
