package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.CountData;
import com.grocery.mcpserver.client.dto.GatewayResponse;
import com.grocery.mcpserver.client.dto.NotificationData;
import com.grocery.mcpserver.client.dto.PageData;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The logged-in user's own in-app notifications (notification-service) through the API gateway: the same two calls as
 * the bell icon of the website, for every role (customer, vendor, delivery partner, admin).
 *
 * Every call carries the user's token (Authorization: Bearer ...). The gateway checks it and tells notification-service
 * who the user is (X-User-Id), so these calls can only return that user's own notifications. Nothing is cached:
 * notifications arrive and are read all the time.
 */
@Component
@RequiredArgsConstructor
public class NotificationClient {

    /** For GatewayCall's message: "The notification list is not available right now. Please try again later." */
    private static final String WHAT = "The notification list";
    private static final ParameterizedTypeReference<GatewayResponse<CountData>> COUNT = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<NotificationData>>> PAGE = new ParameterizedTypeReference<>() { };

    private final RestClient gatewayRestClient;

    /** GET /api/v1/notifications/unread-count */
    public long unreadCount(String userToken) {
        return GatewayCall.dataOf(WHAT, () -> gatewayRestClient.get()
                .uri("/api/v1/notifications/unread-count")
                .headers(headers -> headers.setBearerAuth(userToken))
                .retrieve()
                .body(COUNT)).count();
    }

    /** GET /api/v1/notifications: the newest first; unreadOnly for the unread ones only. */
    public PageData<NotificationData> newest(String userToken, boolean unreadOnly, int size) {
        return GatewayCall.dataOf(WHAT, () -> gatewayRestClient.get()
                .uri("/api/v1/notifications?unreadOnly={unreadOnly}&page=0&size={size}", unreadOnly, size)
                .headers(headers -> headers.setBearerAuth(userToken))
                .retrieve()
                .body(PAGE));
    }
}
