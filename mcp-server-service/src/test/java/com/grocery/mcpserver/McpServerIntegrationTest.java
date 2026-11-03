package com.grocery.mcpserver;

import com.grocery.mcpserver.client.AdminClient;
import com.grocery.mcpserver.client.CatalogClient;
import com.grocery.mcpserver.client.CustomerClient;
import com.grocery.mcpserver.client.NotificationClient;
import com.grocery.mcpserver.client.PartnerClient;
import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.VendorClient;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.tool.TestData;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The whole server on a random port, called like mcp-client-service will call it: with a real MCP client over
 * Streamable HTTP. The gateway clients are mocked, so no other service is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.mcp-api-key=" + McpServerIntegrationTest.API_KEY)
class McpServerIntegrationTest {

    static final String API_KEY = "test-mcp-api-key-0123456789abcdefghij";

    @LocalServerPort
    private int port;

    @MockitoBean
    private CatalogClient catalog;
    @MockitoBean
    private ShopInfoClient shopInfo;
    @MockitoBean
    private CustomerClient customers;
    @MockitoBean
    private VendorClient vendors;
    @MockitoBean
    private PartnerClient partners;
    @MockitoBean
    private AdminClient admin;
    @MockitoBean
    private NotificationClient notifications;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aRealMcpClientFindsAllToolsAndCallsOne() {
        when(shopInfo.stores()).thenReturn(TestData.stores());
        when(catalog.searchProducts(eq("amul butter"), any(), any(), anyBoolean(), anyInt()))
                .thenReturn(new PageData<>(List.of(TestData.amulButter()), 1));

        try (McpSyncClient client = mcpClient(API_KEY)) {
            client.initialize();

            List<String> tools = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
            assertThat(tools).containsExactlyInAnyOrder("searchProducts", "getProductDetails", "getProductReviews",
                    "listCategories", "listCurrentOffers", "getDeliveryRules", "listStores",
                    "getMyCart", "getMyOrders", "getMyOrderDetails",
                    "getVendorStoreToday", "getVendorOrders", "getVendorLowStock", "getVendorSales",
                    "getPartnerStatus", "getPartnerDeliveries", "getPartnerEarnings",
                    "getAdminOverview", "getAdminWaitingWork", "getAdminOrders", "getAdminSales",
                    "getMyUnreadNotificationCount", "getMyNotifications",
                    "prepareMyCartAdd", "prepareMyCartQuantity", "prepareMyCartRemoval", "prepareMyCartClear", "prepareMyOrder",
                    "prepareMyOrderCancellation");

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("searchProducts", Map.of("query", "amul butter")));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            String text = ((McpSchema.TextContent) result.content().getFirst()).text();
            assertThat(text).contains("\"status\":\"OK\"").contains("\"name\":\"Amul Butter\"").contains("\"price\":\"₹56\"")
                    .contains("\"store\":\"Fresh Mart\"");
        }
    }

    @Test
    void theCustomersTokenReachesTheCustomerToolsButIsNoParameterForTheModel() {
        when(customers.latestOrders("customer-jwt", 5)).thenReturn(new PageData<>(List.of(), 0));

        try (McpSyncClient client = mcpClient(API_KEY, "customer-jwt");
             McpSyncClient guestClient = mcpClient(API_KEY)) {
            client.initialize();
            McpSchema.Tool getMyOrderDetails = client.listTools().tools().stream()
                    .filter(tool -> tool.name().equals("getMyOrderDetails")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) getMyOrderDetails.inputSchema().get("properties");
            assertThat(parameters).containsOnlyKeys("orderNumber");

            String withToken = text(client.callTool(new McpSchema.CallToolRequest("getMyOrders", Map.of())));
            assertThat(withToken).contains("\"status\":\"OK\"").contains("The customer has no orders yet.");

            guestClient.initialize();
            String withoutToken = text(guestClient.callTool(new McpSchema.CallToolRequest("getMyOrders", Map.of())));
            assertThat(withoutToken).contains("\"status\":\"LOGIN_REQUIRED\"");
        }
    }

    @Test
    void aVendorsTokenReachesTheVendorToolsAndTheStatusWordIsAParameter() {
        when(vendors.orders("vendor-jwt", "CONFIRMED", 5)).thenReturn(new PageData<>(List.of(), 0));

        try (McpSyncClient client = mcpClient(API_KEY, "vendor-jwt")) {
            client.initialize();
            McpSchema.Tool getVendorOrders = client.listTools().tools().stream()
                    .filter(tool -> tool.name().equals("getVendorOrders")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) getVendorOrders.inputSchema().get("properties");
            assertThat(parameters).containsOnlyKeys("status");
            assertThat((List<?>) getVendorOrders.inputSchema().get("required")).isNullOrEmpty();

            String result = text(client.callTool(new McpSchema.CallToolRequest("getVendorOrders", Map.of("status", "new"))));
            assertThat(result).contains("\"status\":\"OK\"").contains("The store has no new orders.");
        }
    }

    @Test
    void theNotificationToolsUseTheUsersTokenAndHaveNoUserParameter() {
        when(notifications.unreadCount("vendor-jwt")).thenReturn(3L);

        try (McpSyncClient client = mcpClient(API_KEY, "vendor-jwt");
             McpSyncClient guestClient = mcpClient(API_KEY)) {
            client.initialize();
            // Whose notifications is never the model's choice: only the token decides
            assertThat(parametersOf(client, "getMyUnreadNotificationCount")).isNullOrEmpty();
            assertThat(parametersOf(client, "getMyNotifications")).containsOnlyKeys("unreadOnly", "topic");

            String withToken = text(client.callTool(new McpSchema.CallToolRequest("getMyUnreadNotificationCount", Map.of())));
            assertThat(withToken).contains("\"status\":\"OK\"").contains("The user has 3 unread notifications.");

            guestClient.initialize();
            String withoutToken = text(guestClient.callTool(new McpSchema.CallToolRequest("getMyNotifications", Map.of())));
            assertThat(withoutToken).contains("\"status\":\"LOGIN_REQUIRED\"");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parametersOf(McpSyncClient client, String toolName) {
        McpSchema.Tool tool = client.listTools().tools().stream().filter(t -> t.name().equals(toolName)).findFirst().orElseThrow();
        return (Map<String, Object>) tool.inputSchema().get("properties");
    }

    @Test
    void theCartToolsUseTheCustomersTokenAndTakeNoUserIdOrPrice() {
        when(shopInfo.stores()).thenReturn(TestData.stores());
        when(customers.cart("customer-jwt")).thenReturn(TestData.emptyCart());
        when(catalog.searchProducts(any(), any(), any(), anyBoolean(), anyInt()))
                .thenReturn(new PageData<>(List.of(TestData.tomato()), 1));

        try (McpSyncClient client = mcpClient(API_KEY, "customer-jwt");
             McpSyncClient guestClient = mcpClient(API_KEY)) {
            client.initialize();
            // Whose cart and what things cost are never the model's choice: the token decides, cart-service calculates
            assertThat(parametersOf(client, "prepareMyCartAdd")).containsOnlyKeys("product", "quantity", "unit");
            assertThat(parametersOf(client, "prepareMyCartQuantity")).containsOnlyKeys("product", "quantity", "unit");
            assertThat(parametersOf(client, "prepareMyCartRemoval")).containsOnlyKeys("product");
            assertThat(parametersOf(client, "prepareMyCartClear")).isEmpty();
            // the order summary: no customer, address id, price or total from the model either
            assertThat(parametersOf(client, "prepareMyOrder")).containsOnlyKeys("paymentMethod", "address");
            assertThat(parametersOf(client, "prepareMyOrderCancellation")).containsOnlyKeys("orderNumber", "reason");

            // only prepared: the card's productId and the packs, nothing is added
            String prepared = text(client.callTool(new McpSchema.CallToolRequest("prepareMyCartAdd",
                    Map.of("product", "tomatoes", "quantity", 2, "unit", "kg"))));
            assertThat(prepared).contains("\"status\":\"OK\"").contains("NOTHING is added yet")
                    .contains("\"confirm\":{\"productId\":1,\"quantity\":2,\"newQuantity\":2}");

            guestClient.initialize();
            String withoutToken = text(guestClient.callTool(new McpSchema.CallToolRequest("prepareMyCartRemoval", Map.of("product", "tomato"))));
            assertThat(withoutToken).contains("\"status\":\"LOGIN_REQUIRED\"");
        }
    }

    @Test
    void everyToolOnlyReads() {
        // the prepare tools only read: the customer's click on their card changes the cart or places the order
        try (McpSyncClient client = mcpClient(API_KEY)) {
            client.initialize();
            assertThat(client.listTools().tools()).allSatisfy(tool -> {
                assertThat(tool.annotations().readOnlyHint()).isTrue();
                assertThat(tool.annotations().destructiveHint()).isFalse();
            });
        }
    }

    @Test
    void theMcpEndpointNeedsTheRightApiKey() throws Exception {
        String toolsList = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";

        HttpResponse<String> withoutKey = post("/mcp", toolsList, null);
        assertThat(withoutKey.statusCode()).isEqualTo(401);
        assertThat(withoutKey.body()).contains("\"success\":false").contains("A valid X-MCP-API-Key header is required");

        assertThat(post("/mcp", toolsList, "a-wrong-key").statusCode()).isEqualTo(401);
        assertThat(post("/mcp", toolsList, API_KEY).statusCode()).isEqualTo(200);
    }

    @Test
    void healthIsOpenAndEverythingElseIsClosed() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
        assertThat(get("/anything").statusCode()).isEqualTo(401);
    }

    private McpSyncClient mcpClient(String apiKey) {
        return mcpClient(apiKey, null);
    }

    /** Like mcp-client-service: the API key on every request, plus the user's token when a logged-in user chats. */
    private McpSyncClient mcpClient(String apiKey, String userToken) {
        HttpRequest.Builder headers = HttpRequest.newBuilder().header("X-MCP-API-Key", apiKey);
        if (userToken != null) {
            headers.header("X-User-Token", userToken);
        }
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .requestBuilder(headers)
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    private HttpResponse<String> post(String path, String body, String apiKey) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null) {
            request.header("X-MCP-API-Key", apiKey);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
