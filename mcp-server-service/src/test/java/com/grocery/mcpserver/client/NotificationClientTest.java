package com.grocery.mcpserver.client;

import com.grocery.mcpserver.exception.ExternalServiceException;
import com.grocery.mcpserver.exception.LoginRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The user's own notifications: every call carries the user's token; a service that is down is not "log in again". */
class NotificationClientTest {

    /** Real answers of notification-service through the gateway (customer1, 2026-10-28). */
    private static final String UNREAD_PAGE = """
            {"success": true, "message": "Notifications fetched successfully", "data": {"content": [
              {"id": 1699, "userId": 4, "type": "ORDER_CANCELLED", "title": "Order #195 cancelled",
               "message": "Reason: Playwright clean-up.", "referenceId": 195, "read": false, "emailStatus": "SENT",
               "createdAt": "2026-10-28T08:12:44.292314Z"},
              {"id": 1698, "userId": 4, "type": "ORDER_PACKED", "title": "Order #195 packed",
               "message": "Your items are packed and wait for a delivery partner.", "referenceId": 195, "read": false,
               "emailStatus": "SENT", "createdAt": "2026-10-28T08:12:43.714444Z"}],
             "page": 0, "size": 2, "totalElements": 796, "totalPages": 398, "last": false},
             "errors": null, "timestamp": "2026-10-28T12:45:18.912306900Z", "path": null}""";
    private static final String UNREAD_COUNT = """
            {"success": true, "message": "Unread notifications counted", "data": {"count": 796}, "errors": null,
             "timestamp": "2026-10-28T12:45:18.928302800Z", "path": null}""";

    private MockRestServiceServer gateway;
    private NotificationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway");
        gateway = MockRestServiceServer.bindTo(builder).build();
        client = new NotificationClient(builder.build());
    }

    @Test
    void theUnreadCountIsReadWithTheUsersToken() {
        gateway.expect(requestTo("http://gateway/api/v1/notifications/unread-count"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-jwt"))
                .andRespond(withSuccess(UNREAD_COUNT, MediaType.APPLICATION_JSON));

        assertThat(client.unreadCount("user-jwt")).isEqualTo(796);
        gateway.verify();
    }

    @Test
    void theNewestNotificationsAreReadWithTheUsersToken() {
        gateway.expect(requestTo("http://gateway/api/v1/notifications?unreadOnly=true&page=0&size=2"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-jwt"))
                .andRespond(withSuccess(UNREAD_PAGE, MediaType.APPLICATION_JSON));

        var page = client.newest("user-jwt", true, 2);

        assertThat(page.totalElements()).isEqualTo(796);
        assertThat(page.content()).first().satisfies(notification -> {
            assertThat(notification.type()).isEqualTo("ORDER_CANCELLED");
            assertThat(notification.title()).isEqualTo("Order #195 cancelled");
            assertThat(notification.read()).isFalse();
        });
        gateway.verify();
    }

    @Test
    void anExpiredLoginNeedsANewLogin_andAServiceThatIsDownIsUnavailable() {
        gateway.expect(requestTo("http://gateway/api/v1/notifications/unread-count")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.unreadCount("expired-jwt")).isInstanceOf(LoginRequiredException.class);

        gateway.reset();
        // The gateway's answer when notification-service does not answer (circuit breaker, timeout)
        gateway.expect(requestTo("http://gateway/api/v1/notifications/unread-count")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.unreadCount("user-jwt")).isInstanceOf(ExternalServiceException.class)
                .hasMessage("The notification list is not available right now. Please try again later.");
    }
}
