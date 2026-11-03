package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.NotificationClient;
import com.grocery.mcpserver.client.dto.NotificationData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.config.McpTransportConfig;
import com.grocery.mcpserver.exception.ExternalServiceException;
import com.grocery.mcpserver.tool.ToolResult.Status;
import com.grocery.mcpserver.tool.result.NotificationInfo;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationToolsTest {

    private static final McpTransportContext LOGGED_IN = McpTransportContext.create(Map.of(McpTransportConfig.USER_TOKEN, "user-jwt"));
    private static final Instant ARRIVED = Instant.parse("2026-10-28T08:12:44Z");   // 1:42 pm in India

    @Mock
    private NotificationClient notifications;

    private NotificationTools tools;

    @BeforeEach
    void setUp() {
        tools = new NotificationTools(notifications, new ToolRunner());
    }

    @Test
    void withoutALoginTheToolsAskTheUserToLogIn_andNeverCallTheService() {
        assertThat(tools.getMyUnreadNotificationCount(McpTransportContext.EMPTY).status()).isEqualTo(Status.LOGIN_REQUIRED);
        assertThat(tools.getMyNotifications(null, null, McpTransportContext.EMPTY).message())
                .isEqualTo("The user is not logged in. Ask them to log in on the website to see their notifications.");
        verifyNoInteractions(notifications);
    }

    @Test
    void theUnreadCountComesFromNotificationService() {
        when(notifications.unreadCount("user-jwt")).thenReturn(796L, 1L, 0L);

        ToolResult<Long> many = tools.getMyUnreadNotificationCount(LOGGED_IN);
        assertThat(many.status()).isEqualTo(Status.OK);
        assertThat(many.data()).isEqualTo(796);
        assertThat(many.message()).isEqualTo("The user has 796 unread notifications.");
        assertThat(tools.getMyUnreadNotificationCount(LOGGED_IN).message()).isEqualTo("The user has 1 unread notification.");
        assertThat(tools.getMyUnreadNotificationCount(LOGGED_IN).message()).isEqualTo("The user has no unread notifications.");
    }

    @Test
    void theNewestUnreadNotifications_withHowManyThereAreInAll() {
        when(notifications.newest("user-jwt", true, NotificationTools.LIST_SIZE)).thenReturn(new PageData<>(List.of(
                notification("ORDER_CANCELLED", "Order #195 cancelled", false),
                notification("ORDER_PACKED", "Order #195 packed", false)), 796));

        ToolResult<List<NotificationInfo>> result = tools.getMyNotifications(true, null, LOGGED_IN);

        assertThat(result.message()).isEqualTo("The user has 796 unread notifications; these are the 2 newest.");
        assertThat(result.data()).first().isEqualTo(
                new NotificationInfo("Order #195 cancelled", "About your order.", true, "28 Oct 2026, 1:42 pm"));
    }

    @Test
    void allNotifications_andNoneAtAll() {
        when(notifications.newest("user-jwt", false, NotificationTools.LIST_SIZE))
                .thenReturn(new PageData<>(List.of(notification("WELCOME", "Welcome to Fresh Grocery", true)), 1),
                        new PageData<>(List.of(), 0));

        ToolResult<List<NotificationInfo>> one = tools.getMyNotifications(null, null, LOGGED_IN);
        assertThat(one.message()).isEqualTo("The user has 1 notification.");
        assertThat(one.data()).singleElement().satisfies(notification -> assertThat(notification.unread()).isFalse());

        ToolResult<List<NotificationInfo>> none = tools.getMyNotifications(false, " ", LOGGED_IN);
        assertThat(none.status()).isEqualTo(Status.OK);
        assertThat(none.message()).isEqualTo("The user has no notifications.");
    }

    @Test
    void aTopicKeepsItsNotifications_amongTheNewest50() {
        List<NotificationData> newest = List.of(
                notification("WELCOME", "Welcome to Fresh Grocery", false),
                notification("ORDER_CONFIRMED", "Order #36 confirmed", false),
                notification("REFUND_COMPLETED", "Refund for order #35", false),
                notification("STORE_APPROVED", "Your store is approved", false));
        when(notifications.newest("user-jwt", false, NotificationTools.TOPIC_SCAN)).thenReturn(new PageData<>(newest, 4));

        ToolResult<List<NotificationInfo>> orders = tools.getMyNotifications(false, "Orders", LOGGED_IN);
        assertThat(orders.message()).isEqualTo("2 of the user's newest 4 notifications are about orders.");
        assertThat(orders.data()).extracting(NotificationInfo::title).containsExactly("Order #36 confirmed", "Refund for order #35");

        assertThat(tools.getMyNotifications(false, "refunds", LOGGED_IN).message())
                .isEqualTo("1 of the user's newest 4 notifications is about payments.");
        assertThat(tools.getMyNotifications(false, "deliveries", LOGGED_IN).message())
                .isEqualTo("None of the user's newest 4 notifications is about deliveries.");
    }

    @Test
    void aTopicWithManyNotificationsShowsTheNewestFive() {
        List<NotificationData> fifty = IntStream.range(0, 50).mapToObj(i -> notification("NEW_ORDER", "New order #" + (100 - i), false)).toList();
        when(notifications.newest("user-jwt", true, NotificationTools.TOPIC_SCAN)).thenReturn(new PageData<>(fifty, 120));

        ToolResult<List<NotificationInfo>> result = tools.getMyNotifications(true, "order", LOGGED_IN);

        assertThat(result.message()).isEqualTo("50 of the user's newest 50 unread notifications are about orders; these are the 5 newest.");
        assertThat(result.data()).hasSize(5).first().satisfies(notification -> assertThat(notification.title()).isEqualTo("New order #100"));
    }

    @Test
    void anUnknownTopicIsAnInvalidInput_andAServiceThatIsDownIsUnavailable() {
        ToolResult<List<NotificationInfo>> unknown = tools.getMyNotifications(false, "weather", LOGGED_IN);
        assertThat(unknown.status()).isEqualTo(Status.INVALID_INPUT);
        assertThat(unknown.message()).contains("orders, deliveries, payments, store or account");

        when(notifications.unreadCount("user-jwt"))
                .thenThrow(new ExternalServiceException("The notification list is not available right now. Please try again later.", null));
        ToolResult<Long> down = tools.getMyUnreadNotificationCount(LOGGED_IN);
        assertThat(down.status()).isEqualTo(Status.SERVICE_UNAVAILABLE);
        assertThat(down.message()).isEqualTo("The notification list is not available right now. Please try again later.");
    }

    @Test
    void amountsInTheTextsBecomeRupees_likeEveryToolReturnsThem() {
        // The real texts of notification-service: two decimals, no currency
        assertThat(NotificationInfo.from(new NotificationData("ORDER_CONFIRMED", "Order #195 confirmed",
                "Please pay 138.00 in cash on delivery. We will tell you when the store accepts it.", false, ARRIVED)).message())
                .isEqualTo("Please pay ₹138 in cash on delivery. We will tell you when the store accepts it.");
        // At the end of a sentence (found with the real model: "Order total: 150.00.")
        assertThat(NotificationInfo.from(new NotificationData("ORDER_DELIVERED", "Order #194 delivered",
                "Enjoy your groceries! Order total: 150.00.", false, ARRIVED)).message())
                .isEqualTo("Enjoy your groceries! Order total: ₹150.");
        assertThat(NotificationInfo.from(new NotificationData("REFUND_COMPLETED", "Refund for order #35",
                "We refunded 1250.50 (reference RF123) to your account.", false, ARRIVED)).message())
                .isEqualTo("We refunded ₹1,250.50 (reference RF123) to your account.");
        // Order numbers, phone numbers and amounts that already have ₹ stay as they are
        assertThat(NotificationInfo.from(new NotificationData("DELIVERY_PARTNER_ASSIGNED", "A delivery partner for order #194",
                "Arjun Singh (9000000005) will bring your order. Total ₹150.00.", false, ARRIVED)).message())
                .isEqualTo("Arjun Singh (9000000005) will bring your order. Total ₹150.00.");
    }

    @Test
    void aVeryLongTextIsCut() {
        NotificationInfo info = NotificationInfo.from(new NotificationData("NEW_ORDER", "New order", "x".repeat(1000), false, ARRIVED));

        assertThat(info.message()).hasSize(300).endsWith("...");
    }

    private static NotificationData notification(String type, String title, boolean read) {
        return new NotificationData(type, title, "About your order.", read, ARRIVED);
    }
}
