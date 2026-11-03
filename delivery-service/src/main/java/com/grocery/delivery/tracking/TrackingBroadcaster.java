package com.grocery.delivery.tracking;

import com.grocery.delivery.dto.response.TrackingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends a new tracking snapshot to EVERY instance of delivery-service, through a Redis channel (publish/subscribe).
 * Each instance then passes it to the customers whose browser is connected to it (LiveTrackingStreams).
 *
 * Why not send it to the browsers directly? With two or more instances (Kubernetes replicas), the partner's update
 * may arrive at instance A while the customer's live stream is open on instance B. Redis delivers the message to all
 * of them; nothing is stored (a message nobody listens to is simply gone).
 */
@Component
@RequiredArgsConstructor
public class TrackingBroadcaster {

    /** The Redis channel of all live-tracking messages (one JSON TrackingResponse per message). */
    public static final String CHANNEL = "delivery-tracking";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public void publish(TrackingResponse snapshot) {
        redisTemplate.convertAndSend(CHANNEL, jsonMapper.writeValueAsString(snapshot));
    }
}
