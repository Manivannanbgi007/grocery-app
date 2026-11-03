package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.NotificationClient;
import com.grocery.mcpserver.client.dto.NotificationData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.tool.result.NotificationInfo;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The notification tools: the logged-in user's own in-app notifications (the bell icon of the website), for every role:
 * customers, vendors, delivery partners and admins. Read-only, like every tool here: they cannot mark anything as read.
 *
 * The user's token comes from the X-User-Token header of the MCP request (see LoginToken), never from the model, and
 * goes to the gateway with every call. So a tool only ever sees the notifications of the user who is chatting: the model
 * has no parameter for a user id. Without a token the tools answer LOGIN_REQUIRED; when notification-service does not
 * answer, SERVICE_UNAVAILABLE ("The notification list is not available right now ...").
 *
 * mcp-client-service offers these two tools to every logged-in user (GroceryToolsClient), not to guests.
 */
@Component
@RequiredArgsConstructor
public class NotificationTools {

    /** How many notifications a list shows: enough for a short answer */
    static final int LIST_SIZE = 5;
    /** How many of the newest notifications are searched for one topic (notification-service cannot filter by type) */
    static final int TOPIC_SCAN = 50;
    private static final String NOT_LOGGED_IN =
            "The user is not logged in. Ask them to log in on the website to see their notifications.";

    private final NotificationClient notifications;
    private final ToolRunner runner;

    @McpTool(name = "getMyUnreadNotificationCount",
            description = """
                    Only the NUMBER of unread notifications the logged-in user has (the number on the bell icon of the \
                    website), without their texts. Use it for "how many notifications have I not read?", "do I have new \
                    notifications?", "how many unread notifications do I have?". To show or list notifications, also \
                    "show my unread notifications", call getMyNotifications instead.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<Long> getMyUnreadNotificationCount(McpTransportContext context) {
        return runner.run("getMyUnreadNotificationCount", () -> {
            long unread = notifications.unreadCount(LoginToken.of(context, NOT_LOGGED_IN));
            String message = unread == 0
                    ? "The user has no unread notifications."
                    : "The user has " + count(unread, "unread notification") + ".";
            return ToolResult.ok(message, unread);
        });
    }

    @McpTool(name = "getMyNotifications",
            description = """
                    The logged-in user's own newest notifications (at most 5), newest first: title, text, unread or read, \
                    and when each one arrived. Use it for "show my notifications", "what are my latest notifications?", \
                    "show my unread notifications" (unreadOnly true) and "notifications about my orders" (topic orders).""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<NotificationInfo>> getMyNotifications(
            @McpToolParam(description = "true only when the user asks for unread or new notifications; leave it out for "
                    + "\"my notifications\" or \"my latest notifications\" (then read and unread ones)", required = false) Boolean unreadOnly,
            @McpToolParam(description = "Only notifications about: orders, deliveries, payments, store or account",
                    required = false) String topic,
            McpTransportContext context) {
        return runner.run("getMyNotifications", () -> {
            String token = LoginToken.of(context, NOT_LOGGED_IN);
            boolean unread = Boolean.TRUE.equals(unreadOnly);
            String kind = unread ? "unread notification" : "notification";
            if (topic == null || topic.isBlank()) {
                return newest(notifications.newest(token, unread, LIST_SIZE), kind);
            }
            Optional<NotificationTopic> about = NotificationTopic.of(topic);
            if (about.isEmpty()) {
                return ToolResult.invalidInput("topic must be one of: " + NotificationTopic.CHOICES
                        + " (or leave it out for all notifications).");
            }
            return aboutTopic(notifications.newest(token, unread, TOPIC_SCAN), about.get(), kind);
        });
    }

    /** All topics: the newest few, with how many there are in all. */
    private static ToolResult<List<NotificationInfo>> newest(PageData<NotificationData> page, String kind) {
        if (page.content().isEmpty()) {
            return ToolResult.ok("The user has no " + kind + "s.", null);
        }
        List<NotificationInfo> shown = page.content().stream().map(NotificationInfo::from).toList();
        String message = page.totalElements() > shown.size()
                ? "The user has " + count(page.totalElements(), kind) + "; these are the " + shown.size() + " newest."
                : "The user has " + count(shown.size(), kind) + ".";
        return ToolResult.ok(message, shown);
    }

    /** One topic: searched among the newest TOPIC_SCAN notifications, and the message says so. */
    private static ToolResult<List<NotificationInfo>> aboutTopic(PageData<NotificationData> page, NotificationTopic topic,
                                                                 String kind) {
        int searched = page.content().size();
        List<NotificationData> matching = page.content().stream().filter(notification -> topic.covers(notification.type())).toList();
        String newest = "the user's newest " + count(searched, kind);
        if (matching.isEmpty()) {
            return ToolResult.ok(searched == 0
                    ? "The user has no " + kind + "s."
                    : "None of " + newest + " is about " + topic.label() + ".", null);
        }
        List<NotificationInfo> shown = matching.stream().limit(LIST_SIZE).map(NotificationInfo::from).toList();
        String message = matching.size() + " of " + newest + (matching.size() == 1 ? " is" : " are") + " about " + topic.label()
                + (matching.size() > shown.size() ? "; these are the " + shown.size() + " newest." : ".");
        return ToolResult.ok(message, shown);
    }

    /** "1 unread notification", "796 unread notifications" */
    private static String count(long number, String what) {
        return number + " " + what + (number == 1 ? "" : "s");
    }
}
