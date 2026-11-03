package com.grocery.platform;

import com.grocery.platform.constant.HeaderNames;
import com.grocery.platform.constant.KafkaTopics;
import com.grocery.platform.entity.AuditEntry;
import com.grocery.platform.repository.AuditEntryRepository;
import com.jayway.jsonpath.JsonPath;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real MySQL + Kafka: a failed event in grocery.order.events.DLT is listed with the reason, replayed to
 * grocery.order.events (without the error headers), handled there again (here: by the audit log's own consumer),
 * and marked as replayed, so it cannot be replayed twice by mistake.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class DeadLetterIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final String DEAD_LETTERS = KafkaTopics.ORDER_EVENTS + ".DLT";
    private static final Schema ORDER_CREATED = new Schema.Parser().parse("""
            { "type": "record", "name": "OrderCreated", "namespace": "com.grocery.events.order",
              "fields": [ { "name": "eventId", "type": "string" }, { "name": "orderId", "type": "long" } ] }
            """);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditEntryRepository auditEntryRepository;
    @Autowired
    private ConfluentKafkaContainer kafka;

    @Test
    void aFailedEventIsListed_replayedOnce_andHandledAgain() throws Exception {
        long offset = sendDeadLetter("dlt-test-event-1", 77L);

        mockMvc.perform(get("/api/v1/super-admin/dead-letters").header(HeaderNames.USER_ID, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].topic", hasItem(DEAD_LETTERS)));

        String list = mockMvc.perform(get("/api/v1/super-admin/dead-letters/" + DEAD_LETTERS).header(HeaderNames.USER_ID, "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String message = "$.data[?(@.offset == " + offset + ")]";
        assertThat(JsonPath.<List<String>>read(list, message + ".key")).containsExactly("77");
        assertThat(JsonPath.<List<String>>read(list, message + ".eventType")).containsExactly("OrderCreated");
        assertThat(JsonPath.<List<String>>read(list, message + ".eventId")).containsExactly("dlt-test-event-1");
        assertThat(JsonPath.<List<String>>read(list, message + ".originalTopic")).containsExactly(KafkaTopics.ORDER_EVENTS);
        assertThat(JsonPath.<List<Integer>>read(list, message + ".originalOffset")).containsExactly(41);
        assertThat(JsonPath.<List<String>>read(list, message + ".exceptionMessage")).containsExactly("Data too long for column 'event_id'");
        assertThat(JsonPath.<List<Object>>read(list, message + ".replayedAt")).containsExactly((Object) null);

        mockMvc.perform(post("/api/v1/super-admin/dead-letters/" + DEAD_LETTERS + "/replay").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"partition\": 0, \"offset\": " + offset + " }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(1));

        // The event is back on its topic, without the error headers, and a consumer handled it again
        ConsumerRecord<String, byte[]> replayed = await().atMost(WAIT).until(() -> replayedMessage("77"), Objects::nonNull);
        assertThat(header(replayed.headers(), "x-replayed-from")).isEqualTo(DEAD_LETTERS + "/0/" + offset);
        assertThat(header(replayed.headers(), "eventType")).isEqualTo("OrderCreated");
        assertThat(replayed.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNull();
        await().atMost(WAIT).untilAsserted(() -> assertThat(auditEntryRepository.findAll())
                .extracting(AuditEntry::getEventId).contains("dlt-test-event-1"));

        // The replay is in the audit log, so the message shows as replayed and cannot be replayed twice by mistake
        assertThat(auditEntryRepository.findByEventTypeAndAggregateIdStartingWith("DeadLetterReplayed", DEAD_LETTERS + "/"))
                .extracting(AuditEntry::getAggregateId, AuditEntry::getUserId)
                .contains(tuple(DEAD_LETTERS + "/0/" + offset, 1L));
        mockMvc.perform(get("/api/v1/super-admin/dead-letters/" + DEAD_LETTERS).header(HeaderNames.USER_ID, "1"))
                .andExpect(jsonPath(message + ".replayedByUserId").value(1));
        mockMvc.perform(post("/api/v1/super-admin/dead-letters/" + DEAD_LETTERS + "/replay").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"partition\": 0, \"offset\": " + offset + " }"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/super-admin/dead-letters/" + DEAD_LETTERS + "/replay").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"all\": true }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(0));
    }

    @Test
    void onlyDeadLetterTopics_andAClearChoiceOfWhatToReplay() throws Exception {
        mockMvc.perform(get("/api/v1/super-admin/dead-letters/" + KafkaTopics.ORDER_EVENTS).header(HeaderNames.USER_ID, "1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/super-admin/dead-letters/" + DEAD_LETTERS + "/replay").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"partition\": 0, \"all\": true }"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/super-admin/dead-letters/" + DEAD_LETTERS + "/replay").header(HeaderNames.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"partition\": 0, \"offset\": 999999 }"))
                .andExpect(status().isNotFound());
    }

    /** Writes a dead letter the way the error handler does: the Avro event, its headers and the kafka_dlt-* headers. */
    private long sendDeadLetter(String eventId, long orderId) throws Exception {
        GenericRecord event = new GenericData.Record(ORDER_CREATED);
        event.put("eventId", eventId);
        event.put("orderId", orderId);
        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer()) {
            serializer.configure(Map.of("schema.registry.url", "mock://grocery-tests",
                    "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
            byte[] value = serializer.serialize(DEAD_LETTERS, event);

            ProducerRecord<String, byte[]> deadLetter = new ProducerRecord<>(DEAD_LETTERS, String.valueOf(orderId), value);
            deadLetter.headers()
                    .add("eventType", "OrderCreated".getBytes(StandardCharsets.UTF_8))
                    .add(KafkaHeaders.DLT_ORIGINAL_TOPIC, KafkaTopics.ORDER_EVENTS.getBytes(StandardCharsets.UTF_8))
                    .add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(Integer.BYTES).putInt(0).array())
                    .add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(Long.BYTES).putLong(41L).array())
                    .add(KafkaHeaders.DLT_EXCEPTION_FQCN, "org.springframework.kafka.listener.ListenerExecutionFailedException".getBytes(StandardCharsets.UTF_8))
                    .add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "Data too long for column 'event_id'".getBytes(StandardCharsets.UTF_8));
            try (Producer<String, byte[]> producer = new KafkaProducer<>(
                    Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), new ByteArraySerializer())) {
                return producer.send(deadLetter).get(10, TimeUnit.SECONDS).offset();
            }
        }
    }

    /** The replayed message with this key on the original topic, or null while there is none. */
    private ConsumerRecord<String, byte[]> replayedMessage(String key) {
        TopicPartition orderEvents = new TopicPartition(KafkaTopics.ORDER_EVENTS, 0);
        try (Consumer<String, byte[]> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.assign(List.of(orderEvents));
            consumer.seekToBeginning(List.of(orderEvents));
            for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofSeconds(2))) {
                if (key.equals(record.key()) && record.headers().lastHeader("x-replayed-from") != null) {
                    return record;
                }
            }
            return null;
        }
    }

    private static String header(Headers headers, String name) {
        return headers.lastHeader(name) == null ? null : new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
