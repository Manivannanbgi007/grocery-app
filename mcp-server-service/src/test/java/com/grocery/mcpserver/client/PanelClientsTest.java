package com.grocery.mcpserver.client;

import com.grocery.mcpserver.exception.LoginRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The vendor, partner and admin clients: the right addresses, the user's token on every call, and real answers of
 * the gateway (copied from the local backend, shortened) read correctly.
 */
class PanelClientsTest {

    private static final String LOW_STOCK = """
            {"success": true, "message": "Products fetched successfully", "data": {"content": [
              {"id": 7, "storeId": 1, "categoryId": 3, "categoryName": "Dairy & Eggs", "name": "Amul Butter", "unit": "G",
               "unitQuantity": 1E+2, "price": 56.00, "sellingPrice": 56.00, "status": "ACTIVE", "availableQuantity": 3,
               "inStock": true, "lowStock": true}],
             "page": 0, "size": 10, "totalElements": 1, "totalPages": 1, "last": true}}""";
    private static final String SALES = """
            {"success": true, "message": "Daily sales calculated", "data": {"from": "2026-10-26", "to": "2026-10-27", "storeId": 1,
             "days": [{"date": "2026-10-26", "orders": 3, "cancelledOrders": 1, "salesOrders": 2, "itemsTotal": 250.00,
                       "couponDiscounts": 0.00, "deliveryFees": 50.50, "sales": 300.50}],
             "totalSales": 300.50, "totalOrders": 3}}""";
    private static final String EARNINGS = """
            {"success": true, "message": "Earnings calculated", "data": {"from": "2026-10-21", "to": "2026-10-27",
             "deliveries": 34, "total": 1360.00, "days": [{"date": "2026-10-26", "deliveries": 13, "amount": 520.00}]}}""";
    private static final String DASHBOARD = """
            {"success": true, "message": "Dashboard calculated", "data": {"today": "2026-10-27", "totalOrders": 140,
             "ordersToday": 54, "salesToday": 3042.00, "revenueToday": 3042.00, "activeStores": 3, "activeDeliveryPartners": 3,
             "totalCustomers": 139, "activeCustomers": 3, "pendingRefunds": 0, "outOfStockProducts": 3,
             "averageDeliveryMinutes": 0, "generatedAt": "2026-10-27T04:04:36.483295500Z"}}""";
    private static final String NO_DELIVERIES = """
            {"success": true, "message": "Deliveries fetched successfully",
             "data": {"content": [], "page": 0, "size": 1, "totalElements": 0, "totalPages": 0, "last": true}}""";
    private static final String PENDING_STORES = """
            {"success": true, "message": "Stores fetched successfully", "data": {"content": [
              {"id": 4, "ownerUserId": 45, "name": "Green Leaf", "city": "Bengaluru", "status": "PENDING_APPROVAL",
               "statusReason": null, "acceptingOrders": true, "openNow": false, "createdAt": "2026-10-27T02:00:00Z"}],
             "page": 0, "size": 5, "totalElements": 1, "totalPages": 1, "last": true}}""";

    private MockRestServiceServer gateway;
    private VendorClient vendors;
    private PartnerClient partners;
    private AdminClient admin;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway");
        gateway = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        vendors = new VendorClient(restClient);
        partners = new PartnerClient(restClient);
        admin = new AdminClient(restClient);
    }

    @Test
    void theVendorsOrdersOfOneStatusAreCountedWithTheVendorsToken() {
        gateway.expect(requestTo("http://gateway/api/v1/vendor/orders?page=0&size=1&status=CONFIRMED"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer vendor-jwt"))
                .andRespond(withSuccess("""
                        {"success": true, "data": {"content": [], "totalElements": 4}}""", MediaType.APPLICATION_JSON));
        gateway.expect(requestTo("http://gateway/api/v1/vendor/orders?page=0&size=5"))
                .andRespond(withSuccess("""
                        {"success": true, "data": {"content": [], "totalElements": 0}}""", MediaType.APPLICATION_JSON));

        assertThat(vendors.orders("vendor-jwt", "CONFIRMED", 1).totalElements()).isEqualTo(4);
        assertThat(vendors.orders("vendor-jwt", null, 5).totalElements()).isZero();
        gateway.verify();
    }

    @Test
    void theVendorsLowStockAndSalesAreRead() {
        gateway.expect(requestTo("http://gateway/api/v1/vendor/products?lowStockOnly=true&page=0&size=10"))
                .andRespond(withSuccess(LOW_STOCK, MediaType.APPLICATION_JSON));
        gateway.expect(requestTo("http://gateway/api/v1/vendor/reports/sales/daily?from=2026-10-26&to=2026-10-27"))
                .andRespond(withSuccess(SALES, MediaType.APPLICATION_JSON));

        assertThat(vendors.lowStockProducts("vendor-jwt", 10).content()).singleElement().satisfies(product -> {
            assertThat(product.name()).isEqualTo("Amul Butter");
            assertThat(product.unitQuantity()).isEqualByComparingTo("100");
            assertThat(product.availableQuantity()).isEqualTo(3);
        });
        var sales = vendors.dailySales("vendor-jwt", LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27));
        assertThat(sales.totalSales()).isEqualByComparingTo("300.50");
        assertThat(sales.days()).singleElement().satisfies(day -> {
            assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 26));
            assertThat(day.cancelledOrders()).isEqualTo(1);
        });
    }

    @Test
    void aVendorWithoutAStoreGetsNothingAndALoginWithoutTheStoreMustLogInAgain() {
        gateway.expect(requestTo("http://gateway/api/v1/vendor/store")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        gateway.expect(requestTo("http://gateway/api/v1/vendor/orders?page=0&size=5")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThat(vendors.store("vendor-jwt")).isEmpty();
        assertThatThrownBy(() -> vendors.orders("vendor-jwt", null, 5)).isInstanceOf(LoginRequiredException.class)
                .hasMessage("The login has expired, or this account may not see this information. Ask them to log in again on the website.");
    }

    @Test
    void thePartnersEarningsAreReadAndAMissingProfileIsEmpty() {
        gateway.expect(requestTo("http://gateway/api/v1/delivery/earnings?from=2026-10-21&to=2026-10-27"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer partner-jwt"))
                .andRespond(withSuccess(EARNINGS, MediaType.APPLICATION_JSON));
        gateway.expect(requestTo("http://gateway/api/v1/delivery/profile")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        gateway.expect(requestTo("http://gateway/api/v1/delivery/deliveries?active=true&page=0&size=5"))
                .andRespond(withSuccess(NO_DELIVERIES, MediaType.APPLICATION_JSON));

        var earnings = partners.earnings("partner-jwt", LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 27));
        assertThat(earnings.deliveries()).isEqualTo(34);
        assertThat(earnings.total()).isEqualByComparingTo("1360");
        assertThat(earnings.days()).singleElement().satisfies(day -> assertThat(day.amount()).isEqualByComparingTo("520"));
        assertThat(partners.profile("partner-jwt")).isEmpty();
        assertThat(partners.activeDeliveries("partner-jwt", 5).content()).isEmpty();
    }

    @Test
    void theAdminsDashboardAndWaitingStoresAreRead() {
        gateway.expect(requestTo("http://gateway/api/v1/admin/reports/dashboard"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-jwt"))
                .andRespond(withSuccess(DASHBOARD, MediaType.APPLICATION_JSON));
        gateway.expect(requestTo("http://gateway/api/v1/admin/stores?status=PENDING_APPROVAL&page=0&size=5"))
                .andRespond(withSuccess(PENDING_STORES, MediaType.APPLICATION_JSON));

        var dashboard = admin.dashboard("admin-jwt");
        assertThat(dashboard.today()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(dashboard.ordersToday()).isEqualTo(54);
        assertThat(dashboard.averageDeliveryMinutes()).isEqualByComparingTo("0");
        assertThat(admin.stores("admin-jwt", "PENDING_APPROVAL", 5).content()).singleElement().satisfies(store -> {
            assertThat(store.name()).isEqualTo("Green Leaf");
            assertThat(store.createdAt()).isEqualTo(Instant.parse("2026-10-27T02:00:00Z"));
        });
    }

    @Test
    void aNonAdminTokenOnAnAdminEndpointMustLogInAgain() {
        // The gateway answers 403 when a vendor's token asks for the dashboard
        gateway.expect(requestTo("http://gateway/api/v1/admin/reports/dashboard")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> admin.dashboard("vendor-jwt")).isInstanceOf(LoginRequiredException.class);
    }
}
