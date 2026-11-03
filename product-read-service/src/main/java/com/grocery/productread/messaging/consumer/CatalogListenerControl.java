package com.grocery.productread.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Pauses and resumes the catalog listener (CatalogChangesConsumer). scripts/reindex-products.ps1 pauses it while it
 * copies an index, so no change is written to the old index after the copy started; the changes wait in Kafka and are
 * applied, to the new index, after resume. A paused listener keeps its place in the topics and its consumer group.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogListenerControl {

    /** Pausing waits for the batch being applied; Kafka's poll takes at most 5 s, so 15 s is plenty. */
    private static final Duration PAUSE_WAIT = Duration.ofSeconds(15);

    private final KafkaListenerEndpointRegistry registry;

    /** running: the listener has started; paused: it applies no change until resume(). */
    public record Status(boolean running, boolean paused) {
    }

    public Status status() {
        MessageListenerContainer container = container();
        return new Status(container.isRunning(), container.isContainerPaused());
    }

    /** Asks the listener to pause and waits until it has (after the change it is applying). */
    public Status pause() throws InterruptedException {
        MessageListenerContainer container = container();
        container.pause();
        Instant deadline = Instant.now().plus(PAUSE_WAIT);
        while (container.isRunning() && !container.isContainerPaused() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        log.info("Catalog listener paused: {}", container.isContainerPaused());
        return status();
    }

    public Status resume() {
        container().resume();
        log.info("Catalog listener resumed");
        return status();
    }

    private MessageListenerContainer container() {
        return registry.getListenerContainer(CatalogChangesConsumer.LISTENER_ID);
    }
}
