package com.grocery.mcpserver.logging.sample.messaging.consumer;

/** Test sample for LoggingAspectTest: in a "messaging.consumer" package, like a real Kafka consumer. */
public class SampleConsumer {

    public void onEvent() {
        throw new IllegalStateException("Event could not be handled");
    }
}
