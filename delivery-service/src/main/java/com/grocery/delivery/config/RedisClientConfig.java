package com.grocery.delivery.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What the Redis client (Lettuce) does while Redis is down.
 *
 * By default it keeps every command waiting until it has reconnected, up to the command timeout (1 minute unless
 * set). Measured in api-gateway's RedisDownTest: with Redis stopped, ONE request took 60 seconds; here every
 * partner check, location update and assignment would wait a minute too. With REJECT_COMMANDS a command fails at
 * once while the connection is down (PartnerPresenceService then counts the partner as offline, the rest answers 503).
 * The other client options (connect timeout ...) stay as Spring Boot set them from application.yml.
 */
@Configuration(proxyBeanMethods = false)
public class RedisClientConfig {

    @Bean
    LettuceClientConfigurationBuilderCustomizer rejectCommandsWhileRedisIsDown() {
        return builder -> {
            ClientOptions bootOptions = builder.build().getClientOptions().orElseGet(ClientOptions::create);
            builder.clientOptions(bootOptions.mutate()
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .build());
        };
    }
}
