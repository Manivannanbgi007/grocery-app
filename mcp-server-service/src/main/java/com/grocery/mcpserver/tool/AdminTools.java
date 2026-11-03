package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.AdminClient;
import com.grocery.mcpserver.client.dto.DailySalesData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.TopProductData;
import com.grocery.mcpserver.client.dto.TopStoreData;
import com.grocery.mcpserver.tool.result.AdminOverview;
import com.grocery.mcpserver.tool.result.OrderOverview;
import com.grocery.mcpserver.tool.result.SalesSummary;
import com.grocery.mcpserver.tool.result.WaitingWork;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * The admin tools: the platform's numbers, the work waiting for the platform team, orders and sales (read-only).
 *
 * The admin's token comes with the MCP request (see LoginToken) and goes to the gateway with every call; the gateway
 * lets only admin and super admin logins through, so these tools see exactly what the admin panel shows.
 *
 * The names start with "getAdmin": mcp-client-service offers these tools only to admins and the super admin.
 */
@Component
@RequiredArgsConstructor
public class AdminTools {

    static final int LATEST_ORDERS = 5;
    static final int WAITING_LIST = 5;
    static final int TOP_LIST = 5;
    static final String NOT_LOGGED_IN = "The admin is not logged in. Ask them to log in to the admin panel again.";
    private static final String STORES_TO_APPROVE = "PENDING_APPROVAL";
    private static final String KYC_TO_REVIEW = "PENDING";
    private static final String WITHOUT_PARTNER = "WAITING_FOR_PARTNER";

    private final AdminClient admin;
    private final ToolRunner runner;
    private final Clock clock;

    @McpTool(name = "getAdminOverview",
            description = """
                    The platform today, as on the admin dashboard: orders, sales and revenue today, active stores, \
                    partners and customers, refunds waiting, sold-out products, average delivery time, and how much \
                    work waits (stores to approve, KYC to review, deliveries without a partner). Use it for "how is \
                    the shop doing today?", "what needs my attention?", "how many orders today?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<AdminOverview> getAdminOverview(McpTransportContext context) {
        return runner.run("getAdminOverview", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            AdminOverview overview = AdminOverview.from(admin.dashboard(token),
                    admin.stores(token, STORES_TO_APPROVE, 1).totalElements(),
                    admin.partners(token, KYC_TO_REVIEW, 1).totalElements(),
                    admin.deliveries(token, WITHOUT_PARTNER, 1).totalElements());
            return ToolResult.ok("The platform on " + overview.date() + ".", overview);
        });
    }

    @McpTool(name = "getAdminWaitingWork",
            description = """
                    The work waiting for the platform team, by name: new stores to approve, delivery partners whose KYC \
                    waits for a review, and ready orders no delivery partner took (the first 5 of each, with the totals). \
                    Use it for "which stores wait for approval?", "whose KYC must I review?", "which deliveries have no partner?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<WaitingWork> getAdminWaitingWork(McpTransportContext context) {
        return runner.run("getAdminWaitingWork", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            WaitingWork work = WaitingWork.from(admin.stores(token, STORES_TO_APPROVE, WAITING_LIST),
                    admin.partners(token, KYC_TO_REVIEW, WAITING_LIST),
                    admin.deliveries(token, WITHOUT_PARTNER, WAITING_LIST));
            String message = work.storesToApprove() + work.kycToReview() + work.deliveriesWithoutPartner() == 0
                    ? "Nothing is waiting: no store to approve, no KYC to review, every delivery has a partner."
                    : "Waiting: " + count(work.storesToApprove(), "store", "stores") + " to approve, "
                    + work.kycToReview() + " KYC to review, "
                    + count(work.deliveriesWithoutPartner(), "delivery", "deliveries") + " without a partner.";
            return ToolResult.ok(message, work);
        });
    }

    @McpTool(name = "getAdminOrders",
            description = """
                    The newest orders of all stores (at most 5) with order number, store, status, number of items, \
                    total and when they were placed, optionally only orders of one status. Use it for "show the latest \
                    orders", "which orders were cancelled?", "how many orders wait for a store?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<OrderOverview>> getAdminOrders(
            @McpToolParam(description = "Only orders of this status: " + OrderStatusWords.ALLOWED
                    + ". \"new\" = waiting for the store to accept. Leave it out for all orders.", required = false) String status,
            McpTransportContext context) {
        return runner.run("getAdminOrders", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            String code = null;
            if (!OrderStatusWords.meansAll(status)) {
                Optional<String> found = OrderStatusWords.codeOf(status);
                if (found.isEmpty()) {
                    return ToolResult.invalidInput("status must be one of: " + OrderStatusWords.ALLOWED + " (or left out).");
                }
                code = found.get();
            }
            PageData<OrderSummaryData> page = admin.orders(token, code, LATEST_ORDERS);
            String kind = code == null ? "" : " " + status.strip().toLowerCase();
            if (page.content().isEmpty()) {
                return ToolResult.ok("The platform has no" + kind + " orders.", null);
            }
            List<OrderOverview> orders = page.content().stream().map(OrderOverview::from).toList();
            String message = page.totalElements() > orders.size()
                    ? "The platform has " + page.totalElements() + kind + " orders; these are the " + orders.size() + " newest."
                    : "The platform has " + orders.size() + kind + (orders.size() == 1 ? " order." : " orders.");
            return ToolResult.ok(message, orders);
        });
    }

    @McpTool(name = "getAdminSales",
            description = """
                    The whole platform's sales, number of orders and cancelled orders of the last few days, the best \
                    day, the 5 stores with the highest sales and the 5 best-selling products. Use it for "how much did \
                    the shop sell this week?", "which store sells most?", "what are the best sellers?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<SalesSummary> getAdminSales(
            @McpToolParam(description = "How many days up to today: 1 = today, 7 = the last 7 days (the default), "
                    + "30 = the last 30 days. At most 90.", required = false) Integer days,
            McpTransportContext context) {
        return runner.run("getAdminSales", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            if (!DateRange.isValid(days)) {
                return ToolResult.invalidInput(DateRange.DAYS_RULE);
            }
            DateRange range = DateRange.lastDays(days, clock);
            DailySalesData sales = admin.dailySales(token, range.from(), range.to());
            List<TopStoreData> topStores = admin.topStores(token, range.from(), range.to(), TOP_LIST);
            List<TopProductData> bestSellers = admin.topProducts(token, range.from(), range.to(), TOP_LIST);
            return ToolResult.ok("The platform's sales of " + range.text() + ".",
                    SalesSummary.from(range.text(), sales, bestSellers, topStores));
        });
    }

    /** (1, "store", "stores") -> "1 store", (3, ...) -> "3 stores" */
    private static String count(long number, String one, String many) {
        return number + " " + (number == 1 ? one : many);
    }
}
