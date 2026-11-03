package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.VendorClient;
import com.grocery.mcpserver.client.dto.DailySalesData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.StockProductData;
import com.grocery.mcpserver.client.dto.StoreDetailsData;
import com.grocery.mcpserver.client.dto.TopProductData;
import com.grocery.mcpserver.config.McpTransportConfig;
import com.grocery.mcpserver.exception.LoginRequiredException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorToolsTest {

    private static final McpTransportContext LOGGED_IN = McpTransportContext.create(Map.of(McpTransportConfig.USER_TOKEN, "vendor-jwt"));
    /** 26 Oct 2026, 20:00 in UTC is already 27 Oct (01:30) in India: the reports count India days. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T20:00:00Z"), ZoneId.of("Asia/Kolkata"));

    @Mock
    private VendorClient vendors;

    private VendorTools tools;

    @BeforeEach
    void setUp() {
        tools = new VendorTools(vendors, new ToolRunner(), CLOCK);
    }

    @Test
    void withoutALoginTheToolsAskTheVendorToLogIn() {
        assertThat(tools.getVendorStoreToday(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getVendorOrders(null, McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getVendorLowStock(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getVendorSales(7, McpTransportContext.EMPTY).message()).isEqualTo(VendorTools.NOT_LOGGED_IN);
        verifyNoInteractions(vendors);
    }

    @Test
    void theStoreTodayCountsTheOrdersOfEveryStepAndTheLowStock() {
        when(vendors.store("vendor-jwt")).thenReturn(Optional.of(freshMart("ACTIVE", null)));
        when(vendors.orders("vendor-jwt", "CONFIRMED", 1)).thenReturn(count(3));
        when(vendors.orders("vendor-jwt", "ACCEPTED", 1)).thenReturn(count(1));
        when(vendors.orders("vendor-jwt", "PACKED", 1)).thenReturn(count(0));
        when(vendors.orders("vendor-jwt", "READY_FOR_PICKUP", 1)).thenReturn(count(2));
        when(vendors.lowStockProducts("vendor-jwt", 1)).thenReturn(new PageData<>(List.of(), 4));

        var result = tools.getVendorStoreToday(LOGGED_IN);

        assertThat(result.message()).isEqualTo("The store Fresh Mart right now.");
        assertThat(result.data().approval()).isEqualTo("Approved");
        assertThat(result.data().openNow()).isTrue();
        assertThat(result.data().takingOrders()).isTrue();
        assertThat(result.data().newOrders()).isEqualTo(3);
        assertThat(result.data().acceptedOrders()).isEqualTo(1);
        assertThat(result.data().packedOrders()).isZero();
        assertThat(result.data().readyForPickup()).isEqualTo(2);
        assertThat(result.data().lowStockProducts()).isEqualTo(4);
    }

    @Test
    void aStoreWaitingForApprovalHasNoOrderCounts() {
        when(vendors.store("vendor-jwt")).thenReturn(Optional.of(freshMart("PENDING_APPROVAL", null)));

        var result = tools.getVendorStoreToday(LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).isEqualTo("The store Fresh Mart is not approved (Waiting for an admin's approval), so it gets no orders yet.");
        assertThat(result.data().newOrders()).isNull();
        verify(vendors, never()).orders(any(), any(), anyInt());
    }

    @Test
    void aVendorWithoutAStoreIsToldHowToGetOne() {
        when(vendors.store("vendor-jwt")).thenReturn(Optional.empty());

        var result = tools.getVendorStoreToday(LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.message()).isEqualTo(VendorTools.NO_STORE);
    }

    @Test
    void theOrdersCanBeFilteredWithPlainWords() {
        when(vendors.orders("vendor-jwt", "READY_FOR_PICKUP", 5)).thenReturn(new PageData<>(List.of(order(36, "READY_FOR_PICKUP")), 1));
        when(vendors.orders("vendor-jwt", null, 5)).thenReturn(new PageData<>(List.of(order(37, "CONFIRMED"), order(36, "READY_FOR_PICKUP")), 12));

        var ready = tools.getVendorOrders("ready", LOGGED_IN);
        assertThat(ready.message()).isEqualTo("The store has 1 ready order.");
        assertThat(ready.data()).singleElement().satisfies(order -> {
            assertThat(order.orderNumber()).isEqualTo(36);
            assertThat(order.status()).isEqualTo("Packed, waiting for a delivery partner");
            assertThat(order.total()).isEqualTo("₹138");
        });

        var all = tools.getVendorOrders("all", LOGGED_IN);
        assertThat(all.message()).isEqualTo("The store has 12 orders; these are the 2 newest.");

        var wrong = tools.getVendorOrders("shipped", LOGGED_IN);
        assertThat(wrong.status()).isEqualTo(Status.INVALID_INPUT);
        assertThat(wrong.message()).contains("new, pending, accepted");
    }

    @Test
    void noOrdersOfAStepIsSaidPlainly() {
        when(vendors.orders("vendor-jwt", "CONFIRMED", 5)).thenReturn(new PageData<>(List.of(), 0));

        var result = tools.getVendorOrders("new", LOGGED_IN);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.message()).isEqualTo("The store has no new orders.");
        assertThat(result.data()).isNull();
    }

    @Test
    void lowStockShowsTheUnitsLeftAndHiddenProducts() {
        when(vendors.lowStockProducts("vendor-jwt", VendorTools.LOW_STOCK_LIST)).thenReturn(new PageData<>(List.of(
                new StockProductData("Amul Butter", "G", new BigDecimal("100"), "ACTIVE", 3, true),
                new StockProductData("Mango Alphonso", "DOZEN", BigDecimal.ONE, "INACTIVE", 0, false)), 2));

        var result = tools.getVendorLowStock(LOGGED_IN);

        assertThat(result.message()).isEqualTo("2 products are low on stock or sold out.");
        assertThat(result.data().get(0)).satisfies(line -> {
            assertThat(line.product()).isEqualTo("Amul Butter");
            assertThat(line.packSize()).isEqualTo("100 g");
            assertThat(line.stock()).isEqualTo("Low: 3 left");
            assertThat(line.hidden()).isNull();
        });
        assertThat(result.data().get(1)).satisfies(line -> {
            assertThat(line.stock()).isEqualTo("Sold out");
            assertThat(line.hidden()).isEqualTo("Hidden by the vendor");
        });
    }

    @Test
    void salesCoverTheLastDaysUpToTodayInIndia() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        when(vendors.dailySales("vendor-jwt", LocalDate.of(2026, 10, 21), today)).thenReturn(new DailySalesData(
                LocalDate.of(2026, 10, 21), today,
                List.of(new DailySalesData.Day(LocalDate.of(2026, 10, 26), 3, 1, new BigDecimal("300.50")),
                        new DailySalesData.Day(today, 2, 0, new BigDecimal("196.00"))),
                new BigDecimal("496.50"), 5));
        when(vendors.topProducts("vendor-jwt", LocalDate.of(2026, 10, 21), today, VendorTools.BEST_SELLERS))
                .thenReturn(List.of(new TopProductData("Tomato", 60, new BigDecimal("2160.00"))));

        var result = tools.getVendorSales(null, LOGGED_IN);

        assertThat(result.message()).isEqualTo("The store's sales of the last 7 days, 21 Oct 2026 to 27 Oct 2026.");
        assertThat(result.data().sales()).isEqualTo("₹496.50");
        assertThat(result.data().orders()).isEqualTo(5);
        assertThat(result.data().cancelledOrders()).isEqualTo(1);
        assertThat(result.data().bestDay()).isEqualTo("26 Oct 2026 (₹300.50)");
        assertThat(result.data().bestSellers()).singleElement().satisfies(seller -> {
            assertThat(seller.product()).isEqualTo("Tomato");
            assertThat(seller.sold()).isEqualTo(60);
            assertThat(seller.sales()).isEqualTo("₹2,160");
        });
        assertThat(result.data().topStores()).isNull();
    }

    @Test
    void oneDayMeansTodayAndOtherNumbersOfDaysAreRefused() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        when(vendors.dailySales("vendor-jwt", today, today)).thenReturn(new DailySalesData(today, today, List.of(), BigDecimal.ZERO, 0));
        when(vendors.topProducts("vendor-jwt", today, today, VendorTools.BEST_SELLERS)).thenReturn(List.of());

        assertThat(tools.getVendorSales(1, LOGGED_IN).data().period()).isEqualTo("today, 27 Oct 2026");
        assertThat(tools.getVendorSales(0, LOGGED_IN).message()).isEqualTo(DateRange.DAYS_RULE);
        assertThat(tools.getVendorSales(91, LOGGED_IN).status()).isEqualTo(Status.INVALID_INPUT);
    }

    @Test
    void aLoginWithoutTheStoreAsksForANewLogin() {
        // A vendor who logged in before the store was approved: the token has no store yet, the gateway answers 403
        when(vendors.orders("vendor-jwt", null, 5)).thenThrow(new LoginRequiredException("Log in again."));

        assertThat(tools.getVendorOrders(null, LOGGED_IN).status()).isEqualTo(Status.LOGIN_REQUIRED);
    }

    private static StoreDetailsData freshMart(String status, String reason) {
        return new StoreDetailsData(1L, "Fresh Mart", "Bengaluru", status, reason, true, true, Instant.parse("2026-10-01T05:00:00Z"));
    }

    private static PageData<OrderSummaryData> count(long total) {
        return new PageData<>(List.of(), total);
    }

    private static OrderSummaryData order(long id, String status) {
        return new OrderSummaryData(id, "Fresh Mart", status, 3, new BigDecimal("138.00"), Instant.parse("2026-10-26T14:57:33Z"));
    }
}
