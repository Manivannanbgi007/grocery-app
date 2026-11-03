package com.grocery.delivery.config;

import com.grocery.delivery.tracking.LiveTrackingStreams;
import com.grocery.delivery.tracking.TrackingBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Listens on the Redis channel of the live tracking: every instance of delivery-service receives every update and
 * passes it to the customers connected to it (see TrackingBroadcaster and LiveTrackingStreams).
 */
@Configuration
public class RedisTrackingConfig {

    @Bean
    RedisMessageListenerContainer trackingListenerContainer(RedisConnectionFactory connectionFactory, LiveTrackingStreams streams) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(streams, new ChannelTopic(TrackingBroadcaster.CHANNEL));
        return container;
    }
}
