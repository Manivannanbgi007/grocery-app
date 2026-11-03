package com.grocery.mcpclient.tool;

import com.grocery.mcpclient.context.Audience;
import com.grocery.mcpclient.exception.AssistantUnavailableException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * The connection to mcp-server-service (the "grocery-tools" connection in application.yml).
 *
 * Spring AI creates the MCP client from spring.ai.mcp.client.*; it connects on first use (initialized: false),
 * so this service also starts while mcp-server-service is down, and reconnects by itself.
 */
@Slf4j
@Component
public class GroceryToolsClient {

    /**
     * The tools of every logged-in user, whatever their role: their own notifications (the bell icon). They read the
     * user's data with the user's token, so a guest gets none of them.
     */
    public static final Set<String> LOGGED_IN_USER_TOOLS = Set.of("getMyUnreadNotificationCount", "getMyNotifications");

    /**
     * The tool that prepares adding products to the cart. It only reads, like every tool the chat gets (the customer's
     * click on the card adds), but it is run only when the customer's own words ask for a change (RecordingToolCallback):
     * a review or a product description must not make the model offer to add something.
     */
    public static final String ADD_TOOL = "prepareMyCartAdd";

    /**
     * The tools whose amount must be in the customer's own words (ChangeRequest.unsaidAmount): the packs to add and the
     * new quantity, both of which the customer then confirms on a card. A question of theirs (CHOOSE) is the customer's
     * to answer first.
     */
    public static final Set<String> AMOUNT_TOOLS = Set.of(ADD_TOOL, "prepareMyCartQuantity");

    private final McpSyncClient mcpClient;

    public GroceryToolsClient(List<McpSyncClient> mcpClients) {
        if (mcpClients.size() != 1) {
            throw new IllegalStateException("Expected exactly one MCP connection (grocery-tools), found " + mcpClients.size());
        }
        this.mcpClient = mcpClients.getFirst();
    }

    /**
     * The tools for one question, each recording its results in the given recorder.
     * The list is read from mcp-server-service every time (a few milliseconds), so new tools appear without a restart.
     *
     * @param audience who is chatting: everybody gets the shop's tools, and a logged-in user also the tools for their
     *                 own data (see ownerOf). A user's model never sees anybody else's tools, so it cannot try them,
     *                 and a shorter tool list helps a small model to pick the right tool.
     * @throws AssistantUnavailableException when mcp-server-service cannot be reached: without tools the
     *                                       assistant must not answer at all
     */
    public List<ToolCallback> toolsFor(ToolCallRecorder recorder, Audience audience) {
        List<McpSchema.Tool> tools;
        try {
            tools = mcpClient.listTools().tools();
        } catch (RuntimeException ex) {
            throw new AssistantUnavailableException(ex);
        }
        return tools.stream()
                .filter(tool -> isFor(tool.name(), isReadOnly(tool), audience))
                .map(tool -> (ToolCallback) new RecordingToolCallback(mcpClient, tool, recorder))
                .toList();
    }

    /**
     * Whether a tool is in this audience's list. A tool that changes data (not marked read-only, or with no marks at all)
     * is for nobody: the chat only reads and prepares cards, and only the user's own click changes something, so a new
     * tool that changes data can never reach a user by mistake. Of the read-only tools, a tool of every logged-in user
     * (LOGGED_IN_USER_TOOLS) is for anyone who is logged in, a role's own tool (ownerOf) for that role only, and the
     * shop's tools for everybody.
     */
    static boolean isFor(String toolName, boolean readOnly, Audience audience) {
        if (!readOnly) {
            return false;
        }
        if (LOGGED_IN_USER_TOOLS.contains(toolName)) {
            return audience != Audience.GUEST;
        }
        Audience owner = ownerOf(toolName);
        return owner == null || owner == audience;
    }

    /** Only a tool that says so is read-only: no marks at all count as "may change data". */
    private static boolean isReadOnly(McpSchema.Tool tool) {
        return tool.annotations() != null && Boolean.TRUE.equals(tool.annotations().readOnlyHint());
    }

    /**
     * Whose tool it is, by the start of its name: getMy... and prepareMy... (a customer's cart and orders), getVendor...
     * (a vendor's store), getPartner... (a delivery partner's deliveries), getAdmin... (the admins' numbers). null for the
     * shop's tools, which are for everybody. (The tools of every logged-in user are checked first, see isFor.)
     */
    static Audience ownerOf(String toolName) {
        if (toolName.startsWith("getMy") || toolName.startsWith("prepareMy")) {
            return Audience.CUSTOMER;
        }
        if (toolName.startsWith("getVendor")) {
            return Audience.VENDOR;
        }
        if (toolName.startsWith("getPartner")) {
            return Audience.DELIVERY_PARTNER;
        }
        if (toolName.startsWith("getAdmin")) {
            return Audience.ADMIN;
        }
        return null;
    }
}
