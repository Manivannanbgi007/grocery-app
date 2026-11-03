package com.grocery.inventory.config;

import com.grocery.inventory.constant.KafkaTopics;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The dead-letter topic is the last safety net, so an event must never disappear because the DLT itself cannot be
 * written (for example Kafka refuses it). The error handler must then hand the event back to Kafka (seek), so it is
 * read and tried again, instead of moving on past it.
 */
class KafkaConsumerConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void anEventIsNotSkippedWhenTheDeadLetterTopicCannotBeWritten() {
        KafkaTemplate<String, Object> deadLetterTemplate = mock(KafkaTemplate.class);
        when(deadLetterTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("the DLT cannot be written")));
        DefaultErrorHandler errorHandler = new KafkaConsumerConfig().kafkaErrorHandler(deadLetterTemplate);

        List<ConsumerRecord<?, ?>> event = List.of(
                new ConsumerRecord<>(KafkaTopics.ORDER_EVENTS, 0, 42L, "18", "OrderCreated"));
        Consumer<String, Object> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);   // "not running": no real pauses

        // The first try and the 3 retries fail; after the last one the handler writes to the DLT, which fails too
        for (int attempt = 1; attempt <= 4; attempt++) {
            catchThrowable(() -> errorHandler.handleRemaining(
                    new IllegalStateException("handling failed"), event, consumer, container));
        }

        verify(deadLetterTemplate).send(any(ProducerRecord.class));   // the DLT was tried once
        verify(consumer, times(4)).seek(new TopicPartition(KafkaTopics.ORDER_EVENTS, 0), 42L);   // and the event handed back
    }
}
