package com.grocery.order.live;

import com.grocery.order.config.VendorOrderStreamProperties;
import com.grocery.order.dto.response.OrderChange;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The vendors' open live order streams on THIS instance (Server-Sent Events), and what is sent on them.
 *
 * Server-Sent Events: the browser keeps one HTTP response open, and the server writes an event into it whenever
 * something changes. One direction only (server -> browser), which is all the order queue needs; plain HTTP, so it
 * passes the gateway, proxies and the JWT check like any request. What the vendor panel receives:
 *
 *   event: ready                                    once, when the stream is open (the page loads the queue again:
 *   data: {}                                        something may have changed while it was connecting)
 *
 *   event: order                                    an order of the store changed (new, accepted, cancelled ...):
 *   data: {"orderId":194,"status":"CONFIRMED"}      the page loads its lists and that order again
 *
 *   : keep-alive                                    a comment every 15 s, so proxies do not close a quiet stream
 *
 * OrderChangeListener calls sendOrderChange() for every order event that Kafka delivers.
 * The same pattern as delivery-service's LiveTrackingStreams (the customer's tracking page).
 */
@Slf4j
@Component
public class VendorOrderStreams {

    static final String READY_EVENT = "ready";
    static final String ORDER_EVENT = "order";

    private final Map<Long, Set<SseEmitter>> streamsByStore = new ConcurrentHashMap<>();
    private final AtomicInteger openStreams = new AtomicInteger();
    private final JsonMapper jsonMapper;
    private final VendorOrderStreamProperties properties;

    public VendorOrderStreams(JsonMapper jsonMapper, VendorOrderStreamProperties properties, MeterRegistry meterRegistry) {
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        Gauge.builder("order.vendor.streams", openStreams, AtomicInteger::get)
                .description("Vendors' live order streams open on this instance")
                .register(meterRegistry);
    }

    /** true when this instance has as many streams open as it may (the controller then answers 503). */
    public boolean isFull() {
        return openStreams.get() >= properties.maxStreams();
    }

    /** true when at least one vendor follows their orders on this instance. */
    public boolean hasOpenStreams() {
        return openStreams.get() > 0;
    }

    public int openStreams() {
        return openStreams.get();
    }

    /** Opens a stream for the orders of one store and says "ready" at once. */
    public SseEmitter open(Long storeId) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        // compute(...) changes one store's entry in one step, so a stream of the same store that closes at this very
        // moment (remove below) cannot drop the new one with it
        streamsByStore.compute(storeId, (id, emitters) -> {
            Set<SseEmitter> streams = emitters != null ? emitters : ConcurrentHashMap.newKeySet();
            streams.add(emitter);
            return streams;
        });
        openStreams.incrementAndGet();
        emitter.onCompletion(() -> remove(storeId, emitter));   // the stream ended (also after a timeout)
        emitter.onError(error -> remove(storeId, emitter));     // the browser went away (page closed, network lost)
        emitter.onTimeout(emitter::complete);                  // the browser opens a new stream

        send(storeId, emitter, READY_EVENT, "{}");              // an event needs a data line to reach the page
        return emitter;
    }

    /** An order of the store changed: tell the store's streams on this instance (if any are open). */
    public void sendOrderChange(Long storeId, OrderChange change) {
        Set<SseEmitter> emitters = streamsByStore.get(storeId);
        if (emitters == null) {
            return;
        }
        String json = jsonMapper.writeValueAsString(change);
        emitters.forEach(emitter -> send(storeId, emitter, ORDER_EVENT, json));
    }

    /** A comment line on every open stream, every 15 s: proxies keep it open, and the browser knows it is alive. */
    @Scheduled(fixedRateString = "${app.vendor-order-stream.heartbeat-interval}")
    void sendHeartbeats() {
        streamsByStore.forEach((storeId, emitters) -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException ex) {
                remove(storeId, emitter);
            }
        }));
    }

    /**
     * When the service stops: close every stream FIRST, so the graceful shutdown does not wait 30 s for these open
     * requests (a @PreDestroy method would only run after that wait). The browsers reconnect to another instance.
     */
    @EventListener(ContextClosedEvent.class)
    void closeAll() {
        log.info("Closing {} live order streams", openStreams.get());
        streamsByStore.values().forEach(emitters -> emitters.forEach(SseEmitter::complete));
    }

    private void send(Long storeId, SseEmitter emitter, String eventName, String json) {
        try {
            // the JSON is ready as text: send it as it is
            emitter.send(SseEmitter.event().name(eventName).data(json, MediaType.TEXT_PLAIN));
        } catch (IOException | IllegalStateException ex) {
            remove(storeId, emitter);   // the browser is gone (page closed, network lost): Spring closes the stream
        }
    }

    private void remove(Long storeId, SseEmitter emitter) {
        streamsByStore.computeIfPresent(storeId, (id, emitters) -> {
            if (emitters.remove(emitter)) {
                openStreams.decrementAndGet();
            }
            return emitters.isEmpty() ? null : emitters;   // null: the store has no stream left on this instance
        });
    }
}
