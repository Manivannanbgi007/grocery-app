package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.VendorClient;
import com.grocery.mcpserver.client.dto.DailySalesData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.StockProductData;
import com.grocery.mcpserver.client.dto.StoreDetailsData;
import com.grocery.mcpserver.client.dto.TopProductData;
import com.grocery.mcpserver.tool.result.OrderOverview;
import com.grocery.mcpserver.tool.result.SalesSummary;
import com.grocery.mcpserver.tool.result.StockLine;
import com.grocery.mcpserver.tool.result.StoreToday;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * The vendor tools: the logged-in vendor's own store, its orders, stock and sales (read-only, like every tool here).
 *
 * The vendor's token comes with the MCP request (see LoginToken) and goes to the gateway with every call; the
 * services take the store from the token, so a tool can only ever see the store of the vendor who is chatting.
 *
 * The names start with "getVendor": mcp-client-service offers these tools only to logged-in vendors.
 */
@Component
@RequiredArgsConstructor
public class VendorTools {

    static final int LATEST_ORDERS = 5;
    static final int LOW_STOCK_LIST = 10;
    static final int BEST_SELLERS = 5;
    static final String NOT_LOGGED_IN = "The vendor is not logged in. Ask them to log in to the vendor panel again.";
    static final String NO_STORE =
            "This vendor has no store yet. They can register one in the vendor panel; an admin then approves it.";

    private final VendorClient vendors;
    private final ToolRunner runner;
    private final Clock clock;

    @McpTool(name = "getVendorStoreToday",
            description = """
                    The logged-in vendor's own store right now: approved or not, open now, taking orders, how many \
                    orders wait at each step (new, accepted, packed, ready for pickup) and how many products are low \
                    on stock. Use it for "how is my store doing?", "do I have new orders?", "is my store open?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<StoreToday> getVendorStoreToday(McpTransportContext context) {
        return runner.run("getVendorStoreToday", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            Optional<StoreDetailsData> found = vendors.store(token);
            if (found.isEmpty()) {
                return ToolResult.notFound(NO_STORE);
            }
            StoreDetailsData store = found.get();
            if (!"ACTIVE".equals(store.status())) {
                StoreToday today = StoreToday.notApproved(store);
                return ToolResult.ok("The store " + store.name() + " is not approved (" + today.approval()
                        + "), so it gets no orders yet.", today);
            }
            StoreToday today = StoreToday.from(store, count(token, "CONFIRMED"), count(token, "ACCEPTED"),
                    count(token, "PACKED"), count(token, "READY_FOR_PICKUP"), vendors.lowStockProducts(token, 1).totalElements());
            return ToolResult.ok("The store " + store.name() + " right now.", today);
        });
    }

    @McpTool(name = "getVendorOrders",
            description = """
                    The logged-in vendor's own store: its newest orders (at most 5) with order number, status, number \
                    of items, total and when they were placed, optionally only orders of one status. Use it for \
                    "show my new orders", "which orders are packed?", "my latest orders".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<OrderOverview>> getVendorOrders(
            @McpToolParam(description = "Only orders of this status: " + OrderStatusWords.ALLOWED
                    + ". \"new\" = waiting for the store to accept. Leave it out for all orders.", required = false) String status,
            McpTransportContext context) {
        return runner.run("getVendorOrders", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            String code = null;
            if (!OrderStatusWords.meansAll(status)) {
                Optional<String> found = OrderStatusWords.codeOf(status);
                if (found.isEmpty()) {
                    return ToolResult.invalidInput("status must be one of: " + OrderStatusWords.ALLOWED + " (or left out).");
                }
                code = found.get();
            }
            PageData<OrderSummaryData> page = vendors.orders(token, code, LATEST_ORDERS);
            String kind = code == null ? "" : " " + status.strip().toLowerCase();
            if (page.content().isEmpty()) {
                return ToolResult.ok("The store has no" + kind + " orders.", null);
            }
            List<OrderOverview> orders = page.content().stream().map(OrderOverview::from).toList();
            String message = page.totalElements() > orders.size()
                    ? "The store has " + page.totalElements() + kind + " orders; these are the " + orders.size() + " newest."
                    : "The store has " + orders.size() + kind + (orders.size() == 1 ? " order." : " orders.");
            return ToolResult.ok(message, orders);
        });
    }

    @McpTool(name = "getVendorLowStock",
            description = """
                    The logged-in vendor's own products that are low on stock or sold out (at most 10), with the units \
                    left. Use it for "what is running out?", "which products are sold out?", "what should I restock?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<StockLine>> getVendorLowStock(McpTransportContext context) {
        return runner.run("getVendorLowStock", () -> {
            PageData<StockProductData> page = vendors.lowStockProducts(LoginToken.of(context, NOT_LOGGED_IN), LOW_STOCK_LIST);
            if (page.content().isEmpty()) {
                return ToolResult.ok("No product is low on stock.", null);
            }
            List<StockLine> products = page.content().stream().map(StockLine::from).toList();
            String message = page.totalElements() > products.size()
                    ? page.totalElements() + " products are low on stock or sold out; these are the first " + products.size() + "."
                    : products.size() + (products.size() == 1 ? " product is" : " products are") + " low on stock or sold out.";
            return ToolResult.ok(message, products);
        });
    }

    @McpTool(name = "getVendorSales",
            description = """
                    The logged-in vendor's own store: sales, number of orders and cancelled orders of the last few days, \
                    the best day and the 5 best-selling products. Use it for "how much did I sell this week?", \
                    "my sales today", "what sells best?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<SalesSummary> getVendorSales(
            @McpToolParam(description = "How many days up to today: 1 = today, 7 = the last 7 days (the default), "
                    + "30 = the last 30 days. At most 90.", required = false) Integer days,
            McpTransportContext context) {
        return runner.run("getVendorSales", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            if (!DateRange.isValid(days)) {
                return ToolResult.invalidInput(DateRange.DAYS_RULE);
            }
            DateRange range = DateRange.lastDays(days, clock);
            DailySalesData sales = vendors.dailySales(token, range.from(), range.to());
            List<TopProductData> bestSellers = vendors.topProducts(token, range.from(), range.to(), BEST_SELLERS);
            return ToolResult.ok("The store's sales of " + range.text() + ".",
                    SalesSummary.from(range.text(), sales, bestSellers, null));
        });
    }

    private long count(String token, String status) {
        return vendors.orders(token, status, 1).totalElements();
    }
}
