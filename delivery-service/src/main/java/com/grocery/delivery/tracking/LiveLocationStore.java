package com.grocery.delivery.tracking;

import com.grocery.delivery.config.TrackingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * The partner's last position per delivery, in Redis - not in MySQL.
 *
 * A partner sends a position every few seconds. Writing each one to MySQL would fill a table with rows nobody reads
 * again; the map only needs the LAST position. Redis keeps exactly that, for all service instances, and forgets it by
 * itself (app.tracking.location-ttl) when updates stop. When the delivery ends, the position is deleted at once
 * (TrackingStatusPublisher): a partner's location is private and is only kept while a customer needs it.
 *
 *   key:   delivery:location:<deliveryId>     value: LiveTracking as JSON     expires after location-ttl
 */
@Component
@RequiredArgsConstructor
public class LiveLocationStore {

    private static final String KEY_PREFIX = "delivery:location:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final TrackingProperties properties;

    public Optional<LiveTracking> find(Long deliveryId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + deliveryId))
                .map(json -> jsonMapper.readValue(json, LiveTracking.class));
    }

    public void save(Long deliveryId, LiveTracking tracking) {
        redisTemplate.opsForValue().set(KEY_PREFIX + deliveryId, jsonMapper.writeValueAsString(tracking), properties.locationTtl());
    }

    public void delete(Long deliveryId) {
        redisTemplate.delete(KEY_PREFIX + deliveryId);
    }
}
