package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.AdminClient;
import com.grocery.mcpserver.client.dto.AdminDashboardData;
import com.grocery.mcpserver.client.dto.DailySalesData;
import com.grocery.mcpserver.client.dto.DeliveryData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.PartnerData;
import com.grocery.mcpserver.client.dto.StoreDetailsData;
import com.grocery.mcpserver.client.dto.TopProductData;
import com.grocery.mcpserver.client.dto.TopStoreData;
import com.grocery.mcpserver.config.McpTransportConfig;
import com.grocery.mcpserver.tool.ToolResult.Status;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminToolsTest {

    private static final McpTransportContext LOGGED_IN = McpTransportContext.create(Map.of(McpTransportConfig.USER_TOKEN, "admin-jwt"));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    @Mock
    private AdminClient admin;

    private AdminTools tools;

    @BeforeEach
    void setUp() {
        tools = new AdminTools(admin, new ToolRunner(), CLOCK);
    }

    @Test
    void withoutALoginTheToolsAskTheAdminToLogIn() {
        assertThat(tools.getAdminOverview(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getAdminWaitingWork(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getAdminOrders(null, McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getAdminSales(null, McpTransportContext.EMPTY).message()).isEqualTo(AdminTools.NOT_LOGGED_IN);
        verifyNoInteractions(admin);
    }

    @Test
    void theOverviewHasTheDashboardAndTheWaitingWorkWithTheShareWrittenOut() {
        when(admin.dashboard("admin-jwt")).thenReturn(dashboard(139, new BigDecimal("65.4")));
        when(admin.stores("admin-jwt", "PENDING_APPROVAL", 1)).thenReturn(new PageData<>(List.of(), 1));
        when(admin.partners("admin-jwt", "PENDING", 1)).thenReturn(new PageData<>(List.of(), 2));
        when(admin.deliveries("admin-jwt", "WAITING_FOR_PARTNER", 1)).thenReturn(new PageData<>(List.of(), 0));

        var result = tools.getAdminOverview(LOGGED_IN);

        assertThat(result.message()).isEqualTo("The platform on 27 Oct 2026.");
        assertThat(result.data().ordersToday()).isEqualTo(54);
        assertThat(result.data().salesToday()).isEqualTo("₹3,042");
        assertThat(result.data().revenueToday()).isEqualTo("₹2,800.50");
        assertThat(result.data().activeCustomers()).isEqualTo("3 of 139 customers ordered in the last 30 days (2%)");
        assertThat(result.data().averageDeliveryTime()).isEqualTo("65 min from order to door (last 30 days)");
        assertThat(result.data().storesToApprove()).isEqualTo(1);
        assertThat(result.data().kycToReview()).isEqualTo(2);
        assertThat(result.data().deliveriesWithoutPartner()).isZero();
    }

    @Test
    void withoutCustomersOrDeliveriesTheOverviewSaysSoInsteadOfAShare() {
        when(admin.dashboard("admin-jwt")).thenReturn(dashboard(0, null));
        when(admin.stores("admin-jwt", "PENDING_APPROVAL", 1)).thenReturn(new PageData<>(List.of(), 0));
        when(admin.partners("admin-jwt", "PENDING", 1)).thenReturn(new PageData<>(List.of(), 0));
        when(admin.deliveries("admin-jwt", "WAITING_FOR_PARTNER", 1)).thenReturn(new PageData<>(List.of(), 0));

        var result = tools.getAdminOverview(LOGGED_IN);

        assertThat(result.data().activeCustomers()).isEqualTo("3 of 0 customers ordered in the last 30 days");
        assertThat(result.data().averageDeliveryTime()).isEqualTo("No delivered orders in the last 30 days");
    }

    @Test
    void theWaitingWorkNamesTheStoresPartnersAndDeliveries() {
        when(admin.stores("admin-jwt", "PENDING_APPROVAL", AdminTools.WAITING_LIST)).thenReturn(new PageData<>(List.of(
                new StoreDetailsData(4L, "Green Leaf", "Bengaluru", "PENDING_APPROVAL", null, true, false, Instant.parse("2026-10-27T02:00:00Z"))), 1));
        when(admin.partners("admin-jwt", "PENDING", AdminTools.WAITING_LIST)).thenReturn(new PageData<>(List.of(
                new PartnerData("Ravi Teja", "MOTORBIKE", "Koramangala", "PENDING", null, false, null, 0)), 3));
        when(admin.deliveries("admin-jwt", "WAITING_FOR_PARTNER", AdminTools.WAITING_LIST)).thenReturn(new PageData<>(List.of(
                new DeliveryData(77, 905, "WAITING_FOR_PARTNER", "Fresh Mart", "45, 100 Feet Road", "Priya Sharma", "Flat 302",
                        "COD", new BigDecimal("177.00"), null, Instant.parse("2026-10-27T03:15:00Z"))), 1));

        var result = tools.getAdminWaitingWork(LOGGED_IN);

        assertThat(result.message()).isEqualTo("Waiting: 1 store to approve, 3 KYC to review, 1 delivery without a partner.");
        assertThat(result.data().stores()).singleElement().satisfies(store -> {
            assertThat(store.store()).isEqualTo("Green Leaf");
            assertThat(store.registered()).isEqualTo("27 Oct 2026, 7:30 am");
        });
        assertThat(result.data().kycToReview()).isEqualTo(3);
        assertThat(result.data().partners()).singleElement().satisfies(partner -> assertThat(partner.vehicle()).isEqualTo("Motorbike"));
        assertThat(result.data().deliveries()).singleElement().satisfies(delivery -> {
            assertThat(delivery.orderNumber()).isEqualTo(905);
            assertThat(delivery.waitingSince()).isEqualTo("27 Oct 2026, 8:45 am");
        });
    }

    @Test
    void nothingWaitingIsSaidPlainly() {
        when(admin.stores("admin-jwt", "PENDING_APPROVAL", AdminTools.WAITING_LIST)).thenReturn(new PageData<>(List.of(), 0));
        when(admin.partners("admin-jwt", "PENDING", AdminTools.WAITING_LIST)).thenReturn(new PageData<>(List.of(), 0));
        when(admin.deliveries("admin-jwt", "WAITING_FOR_PARTNER", AdminTools.WAITING_LIST)).thenReturn(new PageData<>(List.of(), 0));

        assertThat(tools.getAdminWaitingWork(LOGGED_IN).message())
                .isEqualTo("Nothing is waiting: no store to approve, no KYC to review, every delivery has a partner.");
    }

    @Test
    void theOrdersOfAllStoresCanBeFiltered() {
        when(admin.orders("admin-jwt", "CANCELLED", AdminTools.LATEST_ORDERS)).thenReturn(new PageData<>(List.of(
                new OrderSummaryData(140, "Fresh Mart", "CANCELLED", 3, new BigDecimal("138.00"), Instant.parse("2026-10-27T03:46:39Z"))), 9));

        var result = tools.getAdminOrders("cancelled", LOGGED_IN);

        assertThat(result.message()).isEqualTo("The platform has 9 cancelled orders; these are the 1 newest.");
        assertThat(result.data()).singleElement().satisfies(order -> {
            assertThat(order.store()).isEqualTo("Fresh Mart");
            assertThat(order.status()).isEqualTo("Cancelled");
        });
        assertThat(tools.getAdminOrders("lost", LOGGED_IN).status()).isEqualTo(Status.INVALID_INPUT);
    }

    @Test
    void theSalesHaveTheTopStoresToo() {
        LocalDate from = LocalDate.of(2026, 10, 21);
        when(admin.dailySales("admin-jwt", from, TODAY)).thenReturn(new DailySalesData(from, TODAY,
                List.of(new DailySalesData.Day(TODAY, 54, 4, new BigDecimal("5196.40"))), new BigDecimal("5196.40"), 54));
        when(admin.topStores("admin-jwt", from, TODAY, AdminTools.TOP_LIST))
                .thenReturn(List.of(new TopStoreData("Fresh Mart", 34, new BigDecimal("5196.40"))));
        when(admin.topProducts("admin-jwt", from, TODAY, AdminTools.TOP_LIST))
                .thenReturn(List.of(new TopProductData("Tomato", 60, new BigDecimal("2160.00"))));

        var result = tools.getAdminSales(7, LOGGED_IN);

        assertThat(result.message()).isEqualTo("The platform's sales of the last 7 days, 21 Oct 2026 to 27 Oct 2026.");
        assertThat(result.data().sales()).isEqualTo("₹5,196.40");
        assertThat(result.data().cancelledOrders()).isEqualTo(4);
        assertThat(result.data().topStores()).singleElement().satisfies(store -> {
            assertThat(store.store()).isEqualTo("Fresh Mart");
            assertThat(store.orders()).isEqualTo(34);
            assertThat(store.sales()).isEqualTo("₹5,196.40");
        });
        assertThat(result.data().bestSellers()).singleElement().satisfies(seller -> assertThat(seller.product()).isEqualTo("Tomato"));
    }

    private static AdminDashboardData dashboard(long totalCustomers, BigDecimal averageMinutes) {
        return new AdminDashboardData(TODAY, 140, 54, new BigDecimal("3042.00"), new BigDecimal("2800.50"), 3, 3,
                totalCustomers, 3, 0, 3, averageMinutes);
    }
}
