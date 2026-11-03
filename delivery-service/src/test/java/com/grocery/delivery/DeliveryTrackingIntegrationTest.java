package com.grocery.delivery;

import com.grocery.delivery.constant.HeaderNames;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.delivery.tracking.LiveLocationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live delivery tracking end to end (real MySQL and Redis): the partner shares locations, the customer follows them
 * on the live stream (Server-Sent Events, through Redis publish/subscribe), and the rules of who may do what.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class, RedisTestcontainersConfiguration.class})
@ActiveProfiles("test")
class DeliveryTrackingIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final AtomicLong NEXT_ORDER = new AtomicLong(97_000);
    private static final long PARTNER = 970L;
    private static final long CUSTOMER = 4L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DeliveryRepository deliveryRepository;
    @Autowired
    private DeliveryPartnerRepository partnerRepository;
    @Autowired
    private LiveLocationStore liveLocationStore;

    @BeforeEach
    void partnerExists() {
        if (!partnerRepository.existsByUserId(PARTNER)) {
            partnerRepository.save(DeliveryPartner.register(PARTNER, "Arjun Singh", "9000000005"));
        }
    }

    @Test
    void theCustomerFollowsThePartnerLive_fromAcceptingToDelivering() throws Exception {
        Delivery delivery = assignedDelivery();
        long id = delivery.getId();
        long orderId = delivery.getOrderId();
        mockMvc.perform(post("/api/v1/delivery/deliveries/{id}/accept", id).header(HeaderNames.USER_ID, PARTNER))
                .andExpect(status().isOk());

        // The customer opens the live stream: the current state comes at once
        MvcResult stream = mockMvc.perform(get("/api/v1/delivery-tracking/orders/{orderId}/stream", orderId)
                        .header(HeaderNames.USER_ID, CUSTOMER).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        await().atMost(WAIT).until(() -> content(stream).contains("\"status\":\"ACCEPTED\""));
        assertThat(content(stream)).contains("event:tracking").contains("\"live\":true").contains("Arjun Singh");
        assertThat(stream.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");

        // The partner shares a location: the customer's stream gets it (Redis publish/subscribe)
        sendLocation(id, 12.975123, 77.640456, 15.0, Instant.now())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stored").value(true))
                .andExpect(jsonPath("$.data.route").doesNotExist());   // no routing service in the tests
        await().atMost(WAIT).until(() -> content(stream).contains("12.975123"));

        // ... and so does the snapshot, with both places on the map
        mockMvc.perform(get("/api/v1/delivery-tracking/orders/{orderId}", orderId).header(HeaderNames.USER_ID, CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.location.latitude").value(12.975123))
                .andExpect(jsonPath("$.data.location.accuracyMeters").value(15.0))
                .andExpect(jsonPath("$.data.pickup.location.latitude").value(12.9784))
                .andExpect(jsonPath("$.data.destination.location.longitude").value(77.6412))
                .andExpect(jsonPath("$.data.partner.phone").value("9000000005"));

        // Status changes reach the stream too
        mockMvc.perform(post("/api/v1/delivery/deliveries/{id}/pick-up", id).header(HeaderNames.USER_ID, PARTNER))
                .andExpect(status().isOk());
        await().atMost(WAIT).until(() -> content(stream).contains("\"status\":\"PICKED_UP\""));
        mockMvc.perform(post("/api/v1/delivery/deliveries/{id}/start", id).header(HeaderNames.USER_ID, PARTNER));
        mockMvc.perform(post("/api/v1/delivery/deliveries/{id}/deliver", id).header(HeaderNames.USER_ID, PARTNER))
                .andExpect(status().isOk());

        // Delivered: the last state goes out, the stream ends, and the partner's position is deleted at once
        await().atMost(WAIT).until(() -> content(stream).contains("\"status\":\"DELIVERED\""));
        await().atMost(WAIT).until(() -> liveLocationStore.find(id).isEmpty());
        mockMvc.perform(get("/api/v1/delivery-tracking/orders/{orderId}", orderId).header(HeaderNames.USER_ID, CUSTOMER))
                .andExpect(jsonPath("$.data.live").value(false))
                .andExpect(jsonPath("$.data.location").doesNotExist());
        sendLocation(id, 12.97, 77.64, 10.0, Instant.now())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This delivery is DELIVERED: location sharing has stopped"));
    }

    @Test
    void onlyTheirOwnDelivery_forPartnersAndCustomers() throws Exception {
        Delivery delivery = acceptedDelivery();

        // another partner: the delivery does not exist for them
        mockMvc.perform(put("/api/v1/delivery/deliveries/{id}/location", delivery.getId()).header(HeaderNames.USER_ID, 971L)
                        .contentType(MediaType.APPLICATION_JSON).content(locationJson(12.97, 77.64, 10.0, Instant.now())))
                .andExpect(status().isNotFound());
        // another customer: neither the snapshot nor the stream
        mockMvc.perform(get("/api/v1/delivery-tracking/orders/{orderId}", delivery.getOrderId()).header(HeaderNames.USER_ID, 99L))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/delivery-tracking/orders/{orderId}/stream", delivery.getOrderId()).header(HeaderNames.USER_ID, 99L))
                .andExpect(status().isNotFound());
        // no login (the gateway always sends X-User-Id; without it the service refuses)
        mockMvc.perform(get("/api/v1/delivery-tracking/orders/{orderId}", delivery.getOrderId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sharingStartsWithAccepting() throws Exception {
        Delivery delivery = assignedDelivery();

        sendLocation(delivery.getId(), 12.97, 77.64, 10.0, Instant.now())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Location sharing starts when you accept the delivery"));
    }

    @Test
    void misleadingPositionsAreRefused_andFastOrOldOnesSkipped() throws Exception {
        long id = acceptedDelivery().getId();
        Instant now = Instant.now();

        sendLocation(id, 95.0, 77.64, 10.0, now).andExpect(status().isBadRequest());                     // no such latitude
        sendLocation(id, 12.97, 77.64, 5000.0, now).andExpect(status().isBadRequest())                  // a guess, not GPS
                .andExpect(jsonPath("$.message").value("The location is not precise enough (about 5000 m). Please turn on GPS / precise location"));
        sendLocation(id, 12.97, 77.64, 10.0, now.minus(Duration.ofMinutes(20))).andExpect(status().isBadRequest());   // too old
        sendLocation(id, 12.97, 77.64, 10.0, now.plus(Duration.ofMinutes(5))).andExpect(status().isBadRequest());     // future

        sendLocation(id, 12.970, 77.640, 10.0, now).andExpect(jsonPath("$.data.stored").value(true));
        sendLocation(id, 12.971, 77.641, 10.0, now.plusMillis(500)).andExpect(jsonPath("$.data.stored").value(false));  // too soon
        assertThat(liveLocationStore.find(id)).get().satisfies(t -> assertThat(t.location().latitude()).isEqualTo(12.970));
    }

    // ----- helpers -----

    private Delivery assignedDelivery() {
        Delivery delivery = Delivery.forOrder(NEXT_ORDER.incrementAndGet(), CUSTOMER, 1L, null);
        delivery.setPickup("Fresh Mart", "9000000003", "45, 100 Feet Road, Indiranagar, Bengaluru 560038", "560038");
        delivery.setDrop("Priya Sharma", "9000000004", "12, 5th Cross, Indiranagar, Bengaluru 560038", "560038");
        delivery.setPayment("ONLINE", BigDecimal.ZERO);
        delivery.setCoordinates(new BigDecimal("12.978400"), new BigDecimal("77.640800"),
                new BigDecimal("12.971900"), new BigDecimal("77.641200"));
        delivery.assignTo(PARTNER, Instant.now());
        return deliveryRepository.save(delivery);
    }

    private Delivery acceptedDelivery() throws Exception {
        Delivery delivery = assignedDelivery();
        mockMvc.perform(post("/api/v1/delivery/deliveries/{id}/accept", delivery.getId()).header(HeaderNames.USER_ID, PARTNER))
                .andExpect(status().isOk());
        assertThat(deliveryRepository.findById(delivery.getId())).get()
                .extracting(Delivery::getStatus).isEqualTo(DeliveryStatus.ACCEPTED);
        return delivery;
    }

    private ResultActions sendLocation(long deliveryId, double latitude, double longitude, double accuracy, Instant at) throws Exception {
        return mockMvc.perform(put("/api/v1/delivery/deliveries/{id}/location", deliveryId).header(HeaderNames.USER_ID, PARTNER)
                .contentType(MediaType.APPLICATION_JSON).content(locationJson(latitude, longitude, accuracy, at)));
    }

    private static String locationJson(double latitude, double longitude, double accuracy, Instant at) {
        return """
                { "latitude": %s, "longitude": %s, "accuracyMeters": %s, "recordedAt": "%s" }
                """.formatted(latitude, longitude, accuracy, at);
    }

    private static String content(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
