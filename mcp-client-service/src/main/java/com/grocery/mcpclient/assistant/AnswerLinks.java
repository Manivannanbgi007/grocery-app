package com.grocery.mcpclient.assistant;

import com.grocery.mcpclient.context.Audience;
import com.grocery.mcpclient.dto.response.ChatLink;
import com.grocery.mcpclient.tool.GroceryToolsClient;
import com.grocery.mcpclient.tool.ToolCallRecorder;
import com.grocery.mcpclient.tool.ToolCallRecorder.ToolCall;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The pages of the website that fit an answer, made in code from this question's tool results (never from the
 * model's text, so a model that invents an address cannot create a link):
 *
 * - a product the answer names (searchProducts, getProductDetails, getProductReviews): its page, /products/{id};
 * - an order the answer names (getMyOrders, getMyOrderDetails): /orders/{number};
 * - the page of a tool's topic: the wishlist, the cart, the orders, the stores, the offers, and in the panels
 *   their pages (see PAGES);
 * - the notifications page of the user's own role for the notification tools (NOTIFICATION_PAGES);
 * - the cart for the cart tools, with a card and also after a "no" to adding (REFUSED: e.g. the cart holds another
 *   store's products, which the customer sorts out on the cart page);
 * - for prepareMyOrder: the cart when the cart cannot be ordered yet, the addresses page when there is no address.
 *
 * Only results with status OK count (and REFUSED of the cart tools), at most MAX_LINKS links, each path once, in the
 * order the tools were called.
 * The website checks every path again against its own routes before it shows the link.
 */
public final class AnswerLinks {

    static final int MAX_LINKS = 4;
    public static final ChatLink CART_PAGE = new ChatLink("Your cart", "/cart");
    private static final ChatLink ADDRESSES_PAGE = new ChatLink("Your addresses", "/account/addresses");
    private static final String ORDER_PREPARE_TOOL = "prepareMyOrder";

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final Set<String> PRODUCT_TOOLS = Set.of("searchProducts", "getProductDetails", "getProductReviews");
    private static final Set<String> ORDER_TOOLS = Set.of("getMyOrders", "getMyOrderDetails");
    /** The notification tools are every logged-in user's: each role has its notifications page in its own panel. */
    private static final Map<Audience, ChatLink> NOTIFICATION_PAGES = Map.of(
            Audience.CUSTOMER, new ChatLink("Your notifications", "/account/notifications"),
            Audience.VENDOR, new ChatLink("Notifications", "/vendor/notifications"),
            Audience.DELIVERY_PARTNER, new ChatLink("Notifications", "/delivery/notifications"),
            Audience.ADMIN, new ChatLink("Notifications", "/admin/notifications"));
    /** The page that belongs to a tool's topic. */
    private static final Map<String, ChatLink> PAGES = Map.ofEntries(
            Map.entry("getWishlist", new ChatLink("Your wishlist", "/wishlist")),
            Map.entry("getMyCart", CART_PAGE),
            Map.entry("prepareMyCartAdd", CART_PAGE),
            Map.entry("prepareMyCartRemoval", CART_PAGE),
            Map.entry("prepareMyCartQuantity", CART_PAGE),
            Map.entry("prepareMyCartClear", CART_PAGE),
            Map.entry("getMyOrders", new ChatLink("Your orders", "/orders")),
            Map.entry("listStores", new ChatLink("Our stores", "/stores")),
            Map.entry("listCurrentOffers", new ChatLink("Offers for you", "/#offers")),
            Map.entry("listCategories", new ChatLink("All products", "/shop")),
            Map.entry("getVendorStoreToday", new ChatLink("Dashboard", "/vendor")),
            Map.entry("getVendorSales", new ChatLink("Dashboard", "/vendor")),
            Map.entry("getVendorOrders", new ChatLink("Orders", "/vendor/orders")),
            Map.entry("getVendorLowStock", new ChatLink("Products", "/vendor/products")),
            Map.entry("getPartnerStatus", new ChatLink("Profile", "/delivery/profile")),
            Map.entry("getPartnerDeliveries", new ChatLink("Today's deliveries", "/delivery")),
            Map.entry("getPartnerEarnings", new ChatLink("Earnings", "/delivery/earnings")),
            Map.entry("getAdminOverview", new ChatLink("Dashboard", "/admin")),
            Map.entry("getAdminWaitingWork", new ChatLink("Dashboard", "/admin")),
            Map.entry("getAdminOrders", new ChatLink("Orders", "/admin/orders")),
            Map.entry("getAdminSales", new ChatLink("Reports", "/admin/reports")));

    private AnswerLinks() {
    }

    /**
     * @param answer   the answer that passed the check
     * @param recorder the tool calls of this question
     * @param audience who is asking (whose notifications page)
     */
    public static List<ChatLink> of(String answer, ToolCallRecorder recorder, Audience audience) {
        String lowerAnswer = answer.toLowerCase(Locale.ROOT);
        Map<String, ChatLink> links = new LinkedHashMap<>();
        for (ToolCall call : recorder.calls()) {
            if (GroceryToolsClient.ADD_TOOL.equals(call.toolName()) && "REFUSED".equals(call.status())) {
                links.putIfAbsent(CART_PAGE.path(), CART_PAGE);
            }
            if (ORDER_PREPARE_TOOL.equals(call.toolName())) {
                // a cart that cannot be ordered yet is fixed on the cart page, a missing address on the addresses page; an
                // order that waits for its payment (the cart is empty) is its own page
                long unpaidOrder = call.data().path("orderNumber").asLong(0);
                if (unpaidOrder > 0) {
                    links.putIfAbsent("/orders/" + unpaidOrder, new ChatLink("Order #" + unpaidOrder, "/orders/" + unpaidOrder));
                } else if ("REFUSED".equals(call.status())) {
                    links.putIfAbsent(CART_PAGE.path(), CART_PAGE);
                } else if ("NOT_FOUND".equals(call.status())) {
                    links.putIfAbsent(ADDRESSES_PAGE.path(), ADDRESSES_PAGE);
                }
            }
            JsonNode data = okData(call);
            if (data == null) {
                continue;
            }
            if (PRODUCT_TOOLS.contains(call.toolName())) {
                for (JsonNode product : items(data)) {
                    String name = product.path("name").asString(product.path("productName").asString(""));
                    long id = product.path("productId").asLong(0);
                    if (id > 0 && !name.isBlank() && lowerAnswer.contains(name.toLowerCase(Locale.ROOT))) {
                        links.putIfAbsent("/products/" + id, new ChatLink(name, "/products/" + id));
                    }
                }
            }
            if (ORDER_TOOLS.contains(call.toolName())) {
                for (JsonNode order : items(data)) {
                    long number = order.path("orderNumber").asLong(0);
                    if (number > 0 && Pattern.compile("\\b" + number + "\\b").matcher(answer).find()) {
                        links.putIfAbsent("/orders/" + number, new ChatLink("Order #" + number, "/orders/" + number));
                    }
                }
            }
            ChatLink page = GroceryToolsClient.LOGGED_IN_USER_TOOLS.contains(call.toolName())
                    ? NOTIFICATION_PAGES.get(audience)
                    : PAGES.get(call.toolName());
            if (page != null) {
                links.putIfAbsent(page.path(), page);
            }
        }
        return links.values().stream().limit(MAX_LINKS).toList();
    }

    /** The "data" of a result with status OK (an empty object when the result has no data), else null. */
    private static JsonNode okData(ToolCall call) {
        if (call.failed()) {
            return null;
        }
        try {
            JsonNode result = JSON.readTree(call.result());
            return "OK".equals(result.path("status").asString("")) ? result.path("data") : null;
        } catch (JacksonException ex) {
            return null;
        }
    }

    /** A list's items, or the one object. */
    private static Collection<JsonNode> items(JsonNode data) {
        if (data.isArray()) {
            return data.values();
        }
        return data.isObject() ? List.of(data) : new ArrayList<>();
    }
}
