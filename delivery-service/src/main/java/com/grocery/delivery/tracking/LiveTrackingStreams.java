package com.grocery.delivery.tracking;

import com.grocery.delivery.config.TrackingProperties;
import com.grocery.delivery.dto.response.TrackingResponse;
import com.grocery.delivery.exception.ServiceUnavailableException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The customers' open live streams on THIS instance (Server-Sent Events), and what is sent on them.
 *
 * Server-Sent Events: the browser keeps one HTTP response open, and the server writes an event into it whenever
 * something changes. One direction only (server -> browser), which is all a tracking page needs; plain HTTP, so it
 * passes the gateway, proxies and the JWT check like any request.
 *
 *   event: tracking
 *   data: {"orderId":18,"status":"OUT_FOR_DELIVERY","location":{"latitude":12.97,...},...}
 *
 *   : keep-alive            (a comment line every 15 s, so proxies do not close a quiet stream)
 *
 * Every message on the Redis channel (TrackingBroadcaster) arrives here (onMessage) and goes to the streams of that
 * order. When the delivery is over (delivered, failed, cancelled ...) the last snapshot is sent and the stream closed.
 */
@Slf4j
@Component
public class LiveTrackingStreams implements MessageListener {

    static final String EVENT_NAME = "tracking";

    private final Map<Long, Set<SseEmitter>> streamsByOrder = new ConcurrentHashMap<>();
    private final AtomicInteger openStreams = new AtomicInteger();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tracking-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final JsonMapper jsonMapper;
    private final TrackingProperties properties;

    public LiveTrackingStreams(JsonMapper jsonMapper, TrackingProperties properties, MeterRegistry meterRegistry) {
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        Gauge.builder("delivery.tracking.streams", openStreams, AtomicInteger::get)
                .description("Customers' live tracking streams open on this instance")
                .register(meterRegistry);
        long every = properties.heartbeatInterval().toMillis();
        heartbeat.scheduleAtFixedRate(this::sendHeartbeats, every, every, TimeUnit.MILLISECONDS);
    }

    /** Opens a stream for one order and sends the current snapshot at once. */
    public SseEmitter open(TrackingResponse snapshot) {
        if (openStreams.get() >= properties.maxStreams()) {
            throw new ServiceUnavailableException("Live tracking is very busy right now. The page will try again in a moment.");
        }
        Long orderId = snapshot.orderId();
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        streamsByOrder.computeIfAbsent(orderId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        openStreams.incrementAndGet();
        emitter.onCompletion(() -> remove(orderId, emitter));   // also after a timeout or an error
        emitter.onTimeout(emitter::complete);                  // the browser opens a new stream

        send(orderId, emitter, jsonMapper.writeValueAsString(snapshot));
        if (snapshot.status().isOver()) {
            emitter.complete();                                 // nothing more will happen: just the final state
        }
        return emitter;
    }

    /** A snapshot from the Redis channel: send it to the streams of its order (if any are open on this instance). */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        TrackingResponse snapshot = jsonMapper.readValue(json, TrackingResponse.class);
        Set<SseEmitter> emitters = streamsByOrder.get(snapshot.orderId());
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            send(snapshot.orderId(), emitter, json);
            if (snapshot.status().isOver()) {
                emitter.complete();
            }
        }
    }

    public int openStreams() {
        return openStreams.get();
    }

    private void send(Long orderId, SseEmitter emitter, String json) {
        try {
            // the JSON is ready as text: send it as it is
            emitter.send(SseEmitter.event().name(EVENT_NAME).data(json, MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException ex) {
            remove(orderId, emitter);   // the browser is gone (page closed, network lost): Spring closes the stream
        }
    }

    private void sendHeartbeats() {
        streamsByOrder.forEach((orderId, emitters) -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException ex) {
                remove(orderId, emitter);
            }
        }));
    }

    private void remove(Long orderId, SseEmitter emitter) {
        Set<SseEmitter> emitters = streamsByOrder.get(orderId);
        if (emitters != null && emitters.remove(emitter)) {
            openStreams.decrementAndGet();
            if (emitters.isEmpty()) {
                streamsByOrder.remove(orderId, emitters);
            }
        }
    }

    /** On shutdown: close every stream (the browsers reconnect to another instance). */
    @PreDestroy
    void closeAll() {
        heartbeat.shutdownNow();
        streamsByOrder.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
        log.info("Closed {} live tracking streams", openStreams.get());
    }
}
