package com.grocery.delivery.presence;

import com.grocery.delivery.config.DeliveryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Redis down must not fail the request or the Kafka event that asks whether a partner is online. */
@ExtendWith(OutputCaptureExtension.class)
class PartnerPresenceServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final PartnerPresenceService presence =
            new PartnerPresenceService(redisTemplate, mock(DeliveryProperties.class), Clock.systemUTC());

    @Test
    void anOnlinePartner_isOnline() {
        when(redisTemplate.hasKey("delivery:partner-online:7")).thenReturn(true);

        assertThat(presence.isOnline(7L)).isTrue();
    }

    @Test
    void whileRedisIsDown_everyPartnerCountsAsOffline_withOneWarningOnly_andTheReturnIsLogged(CapturedOutput output) {
        when(redisTemplate.hasKey("delivery:partner-online:7"))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to localhost:6379"))
                .thenThrow(new RedisSystemException("Currently not connected. Commands are rejected.", null))
                .thenReturn(true);

        assertThat(presence.isOnline(7L)).isFalse();
        assertThat(presence.isOnline(7L)).isFalse();
        assertThat(presence.isOnline(7L)).isTrue();   // Redis is back

        assertThat(output.getOut().split("Redis cannot be reached", -1)).hasSize(2);   // one WARN, not one per call
        assertThat(output.getOut()).contains("Redis can be reached again");
    }
}
