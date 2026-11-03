package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.CustomerClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.OrderSummaryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.StoreData;
import com.grocery.mcpserver.exception.ExternalServiceException;
import com.grocery.mcpserver.tool.result.CartSummary;
import com.grocery.mcpserver.tool.result.OrderDetails;
import com.grocery.mcpserver.tool.result.OrderOverview;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The customer tools: the logged-in customer's own cart and orders (read-only, like every tool here).
 *
 * The customer's token comes from the X-User-Token header of the MCP request (see LoginToken); Spring AI passes it in
 * as the McpTransportContext parameter, which is not shown to the model. Without a token the tools answer
 * LOGIN_REQUIRED. The token goes to the gateway with every call, so a tool can only ever see the data of the
 * customer who is chatting - the model cannot ask for anybody else's.
 *
 * The names start with "getMy": mcp-client-service offers these tools only to logged-in customers.
 */
@Component
@RequiredArgsConstructor
public class CustomerTools {

    static final int LATEST_ORDERS = 5;
    private static final String NOT_LOGGED_IN =
            "The customer is not logged in. Ask them to log in on the website to see their cart and orders.";

    private final CustomerClient customers;
    private final ShopInfoClient shopInfo;
    private final ToolRunner runner;

    @McpTool(name = "getMyCart",
            description = """
                    The logged-in customer's own shopping cart: products, quantities, totals, coupon, delivery fee and \
                    the amount to pay. Use it for questions like "what is in my cart?" or "how much do I have to pay?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<CartSummary> getMyCart(McpTransportContext context) {
        return runner.run("getMyCart", () -> {
            CartData cart = customers.cart(customerToken(context));
            if (cart.items() == null || cart.items().isEmpty()) {
                return ToolResult.ok("The cart is empty.", null);
            }
            String message = "The cart has " + cart.items().size() + (cart.items().size() == 1 ? " product." : " products.");
            return ToolResult.ok(message, CartSummary.from(cart, storeName(cart.storeId())));
        });
    }

    @McpTool(name = "getMyOrders",
            description = """
                    The logged-in customer's own newest orders (at most 5): order number, store, status, total and when \
                    it was placed. Use it for "where is my order?", "show my orders", "did my order arrive?".""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<OrderOverview>> getMyOrders(McpTransportContext context) {
        return runner.run("getMyOrders", () -> {
            PageData<OrderSummaryData> page = customers.latestOrders(customerToken(context), LATEST_ORDERS);
            if (page.content().isEmpty()) {
                return ToolResult.ok("The customer has no orders yet.", null);
            }
            List<OrderOverview> orders = page.content().stream().map(OrderOverview::from).toList();
            String message = page.totalElements() > orders.size()
                    ? "The customer has " + page.totalElements() + " orders; these are the " + orders.size() + " newest."
                    : "The customer has " + orders.size() + (orders.size() == 1 ? " order." : " orders.");
            return ToolResult.ok(message, orders);
        });
    }

    @McpTool(name = "getMyOrderDetails",
            description = """
                    One of the logged-in customer's own orders in detail: status and its latest step, products, amounts, \
                    payment, whether it can still be cancelled, and the delivery partner. Needs the order number \
                    (from getMyOrders, or from the customer, e.g. "order 36").""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<OrderDetails> getMyOrderDetails(
            @McpToolParam(description = "The order number, e.g. 36") Long orderNumber,
            McpTransportContext context) {
        return runner.run("getMyOrderDetails", () -> {
            String token = customerToken(context);
            if (orderNumber == null || orderNumber <= 0) {
                return ToolResult.invalidInput("orderNumber must be the number of one of the customer's orders, e.g. 36 "
                        + "(getMyOrders lists them).");
            }
            return customers.order(token, orderNumber)
                    .map(order -> ToolResult.ok("Order " + orderNumber + ".", OrderDetails.from(order)))
                    .orElseGet(() -> ToolResult.notFound("The customer has no order with the number " + orderNumber + "."));
        });
    }

    private static String customerToken(McpTransportContext context) {
        return LoginToken.of(context, NOT_LOGGED_IN);
    }

    /** The cart's store name, for "your cart has products from Fresh Mart"; without the store list just no name. */
    private String storeName(Long storeId) {
        if (storeId == null) {
            return null;
        }
        try {
            return shopInfo.stores().stream()
                    .filter(store -> storeId.equals(store.id()))
                    .map(StoreData::name)
                    .findFirst()
                    .orElse(null);
        } catch (ExternalServiceException ex) {
            return null;
        }
    }
}
