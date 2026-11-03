package com.grocery.delivery.presence;

import com.grocery.delivery.config.DeliveryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Which delivery partners are ONLINE right now, kept in Redis.
 *
 * "Online" is a Redis key with an expiry time (app.delivery.online-for, e.g. 30 minutes). The partner app
 * calls "go online" again from time to time; when it stops (phone off, no network) the key simply expires
 * and the partner gets no new deliveries. Redis is a good fit: fast, and it deletes old keys by itself.
 *
 *   key:   delivery:partner-online:<userId>     value: online until (e.g. "2026-10-26T10:30:00Z")     expires after onlineFor
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerPresenceService {

    private static final String KEY_PREFIX = "delivery:partner-online:";

    private final StringRedisTemplate redisTemplate;
    private final DeliveryProperties deliveryProperties;
    private final Clock clock;
    /** false while Redis cannot be reached: logged once when it goes, once when it comes back */
    private final AtomicBoolean redisReachable = new AtomicBoolean(true);

    /** @return until when the partner counts as online */
    public Instant goOnline(Long partnerUserId) {
        Instant onlineUntil = Instant.now(clock).plus(deliveryProperties.onlineFor());
        redisTemplate.opsForValue().set(KEY_PREFIX + partnerUserId, onlineUntil.toString(), deliveryProperties.onlineFor());
        return onlineUntil;
    }

    public void goOffline(Long partnerUserId) {
        redisTemplate.delete(KEY_PREFIX + partnerUserId);
    }

    /**
     * When Redis cannot be reached the answer is "no" (fail safe): nobody gets a delivery automatically until it is
     * back (an admin can assign by hand; partners call "go online" regularly, which assigns again). The request or
     * Kafka event that asked goes on instead of failing. One WARN when Redis goes away, one INFO when it is back.
     */
    public boolean isOnline(Long partnerUserId) {
        try {
            boolean online = Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + partnerUserId));
            if (!redisReachable.getAndSet(true)) {
                log.info("Redis can be reached again: partner presence works again");
            }
            return online;
        } catch (DataAccessException ex) {
            if (redisReachable.getAndSet(false)) {
                log.warn("Redis cannot be reached ({}): partners count as offline, no delivery is assigned automatically until it is back",
                        ex.getMessage());
            }
            return false;
        }
    }

    /** Empty when the partner is offline. */
    public Optional<Instant> onlineUntil(Long partnerUserId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + partnerUserId)).map(Instant::parse);
    }
}
