package com.grocery.mcpclient.tool;

import com.grocery.mcpclient.context.Audience;
import com.grocery.mcpclient.exception.AssistantUnavailableException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroceryToolsClientTest {

    @Mock
    private McpSyncClient mcpClient;

    @Test
    void everybodyGetsTheShopsToolsAndOnlyTheirOwnDataTools() {
        when(mcpClient.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("searchProducts"),
                tool("getMyCart"), tool("getMyOrders"), tool("getVendorOrders"), tool("getPartnerEarnings"),
                tool("getAdminOverview")), null));
        GroceryToolsClient client = new GroceryToolsClient(List.of(mcpClient));

        assertThat(names(client.toolsFor(new ToolCallRecorder(), Audience.GUEST))).containsExactly("searchProducts");
        assertThat(names(client.toolsFor(new ToolCallRecorder(), Audience.CUSTOMER))).containsExactly("searchProducts", "getMyCart", "getMyOrders");
        assertThat(names(client.toolsFor(new ToolCallRecorder(), Audience.VENDOR))).containsExactly("searchProducts", "getVendorOrders");
        assertThat(names(client.toolsFor(new ToolCallRecorder(), Audience.DELIVERY_PARTNER))).containsExactly("searchProducts", "getPartnerEarnings");
        assertThat(names(client.toolsFor(new ToolCallRecorder(), Audience.ADMIN))).containsExactly("searchProducts", "getAdminOverview");
    }

    @Test
    void everyLoggedInUserGetsTheNotificationTools_guestsDoNot() {
        when(mcpClient.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("searchProducts"),
                tool("getMyUnreadNotificationCount"), tool("getMyNotifications"), tool("getMyCart"), tool("getVendorOrders")), null));
        GroceryToolsClient client = new GroceryToolsClient(List.of(mcpClient));

        assertThat(names(client.toolsFor(new ToolCallRecorder(), Audience.GUEST))).containsExactly("searchProducts");
        assertThat(names(client.toolsFor(new ToolCallRecorder(), Audience.CUSTOMER)))
                .containsExactly("searchProducts", "getMyUnreadNotificationCount", "getMyNotifications", "getMyCart");
        // getMy... is the customers' prefix, but the notification tools are every role's
        for (Audience panel : List.of(Audience.VENDOR, Audience.DELIVERY_PARTNER, Audience.ADMIN)) {
            assertThat(names(client.toolsFor(new ToolCallRecorder(), panel)))
                    .contains("getMyUnreadNotificationCount", "getMyNotifications").doesNotContain("getMyCart");
        }
    }

    @Test
    void onlyCustomersGetTheCartTools_andNoToolThatChangesData() {
        // every cart tool only prepares a card; the old addToMyCart of an mcp-server-service that is not updated yet
        // changes the cart at once, so nobody gets it
        when(mcpClient.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("searchProducts"), tool("getMyCart"),
                change("addToMyCart"), tool("prepareMyCartAdd"), tool("prepareMyCartQuantity"), tool("prepareMyCartRemoval"),
                tool("prepareMyCartClear"), tool("prepareMyOrder")), null));
        GroceryToolsClient client = new GroceryToolsClient(List.of(mcpClient));

        assertThat(names(client.toolsFor(new ToolCallRecorder(), Audience.CUSTOMER))).containsExactly("searchProducts",
                "getMyCart", "prepareMyCartAdd", "prepareMyCartQuantity", "prepareMyCartRemoval", "prepareMyCartClear", "prepareMyOrder");
        for (Audience other : List.of(Audience.GUEST, Audience.VENDOR, Audience.DELIVERY_PARTNER, Audience.ADMIN)) {
            assertThat(names(client.toolsFor(new ToolCallRecorder(), other)))
                    .doesNotContain("addToMyCart", "prepareMyCartAdd", "prepareMyCartRemoval", "prepareMyOrder");
        }
    }

    @Test
    void aToolThatMayChangeDataAndIsNotListedIsOfferedToNobody() {
        // e.g. a new tool on mcp-server-service, or one whose read-only mark was forgotten: it fails safe. (Also the old
        // removeFromMyCart and setMyCartQuantity of an mcp-server-service that is not updated yet: those changes need the
        // customer's click now.)
        McpSchema.Tool unmarked = McpSchema.Tool.builder().name("getMyAddresses").description("x").inputSchema(Map.of("type", "object")).build();
        when(mcpClient.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool("searchProducts"),
                change("emptyMyCart"), change("removeFromMyCart"), change("setMyCartQuantity"), unmarked), null));
        GroceryToolsClient client = new GroceryToolsClient(List.of(mcpClient));

        for (Audience audience : Audience.values()) {
            assertThat(names(client.toolsFor(new ToolCallRecorder(), audience))).containsExactly("searchProducts");
        }
    }

    @Test
    void withoutTheToolServerTheAssistantIsUnavailable() {
        when(mcpClient.listTools()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> new GroceryToolsClient(List.of(mcpClient)).toolsFor(new ToolCallRecorder(), Audience.GUEST))
                .isInstanceOf(AssistantUnavailableException.class);
    }

    /** A read-only tool, marked like mcp-server-service marks them */
    private static McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder().name(name).description(name).inputSchema(Map.of("type", "object"))
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false).build())
                .build();
    }

    /** A tool that changes data */
    private static McpSchema.Tool change(String name) {
        return McpSchema.Tool.builder().name(name).description(name).inputSchema(Map.of("type", "object"))
                .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(false).build())
                .build();
    }

    private static List<String> names(List<ToolCallback> tools) {
        return tools.stream().map(tool -> tool.getToolDefinition().name()).toList();
    }
}
