package com.grocery.cart.messaging.consumer;

import com.grocery.cart.KafkaTestcontainersConfiguration;
import com.grocery.cart.TestcontainersConfiguration;
import com.grocery.cart.constant.KafkaTopics;
import com.grocery.cart.entity.Cart;
import com.grocery.cart.entity.CartItem;
import com.grocery.cart.entity.Coupon;
import com.grocery.cart.mapper.CartMapper;
import com.grocery.cart.pricing.PricingRulesProvider;
import com.grocery.cart.repository.CartRepository;
import com.grocery.cart.repository.CouponRedemptionRepository;
import com.grocery.cart.repository.CouponRepository;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderLine;
import com.grocery.events.platform.PlatformSettingsChanged;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.grocery.cart.support.TestData.flatCoupon;
import static com.grocery.cart.support.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * OrderCreated empties the ordered products from the cart and counts the coupon use;
 * OrderCancelled frees the coupon use again (real MySQL and real Kafka).
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
@ActiveProfiles("test")
class OrderEventsConsumerIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(60);
    private static final long CUSTOMER = 800L;
    private static final long ORDER = 8001L;

    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private CouponRedemptionRepository couponRedemptionRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ConfluentKafkaContainer kafka;
    @Autowired
    private PricingRulesProvider pricingRulesProvider;

    @Test
    void orderCreated_thenCancelled() {
        Coupon coupon = flatCoupon("ONCEONLY", "20.00", "0.00");
        coupon.setId(null);
        coupon.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));
        coupon.setValidUntil(Instant.now().plus(30, ChronoUnit.DAYS));
        Long couponId = couponRepository.save(coupon).getId();

        // The customer ordered Tomato (with the coupon), then added Onion before the event arrived
        CartMapper cartMapper = new CartMapper();
        Cart cart = Cart.newCart(CUSTOMER);
        cart.addItem(1L, cartMapper.toCartItem(product(1, 1, "Tomato", "36.00", 50), 2));
        cart.addItem(1L, cartMapper.toCartItem(product(2, 1, "Onion", "35.00", 50), 1));
        cart.applyCoupon("ONCEONLY");
        cartRepository.save(cart);

        send(OrderCreated.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(ORDER).setCustomerUserId(CUSTOMER).setStoreId(1L)
                .setItems(List.of(new OrderLine(1L, "Tomato", 2, "36.00")))
                .setItemsTotal("72.00").setCouponCode("ONCEONLY").setCouponDiscount("20.00").setDeliveryFee("30.00")
                .setTotalAmount("82.00").setPaymentMethod("COD")
                .build());

        await().atMost(WAIT).untilAsserted(() -> assertThat(couponRedemptionRepository.existsByOrderId(ORDER)).isTrue());
        assertThat(couponRepository.findById(couponId).orElseThrow().getUsedCount()).isEqualTo(1);
        transactionTemplate.executeWithoutResult(status -> {
            Cart updated = cartRepository.findByCustomerUserId(CUSTOMER).orElseThrow();
            assertThat(updated.getItems()).extracting(CartItem::getProductName).containsExactly("Onion");   // added later: kept
            assertThat(updated.getCouponCode()).isNull();
        });

        send(OrderCancelled.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setOrderId(ORDER).setCustomerUserId(CUSTOMER).setStoreId(1L)
                .setReason("Changed my mind").setCancelledBy("CUSTOMER")
                .build());

        await().atMost(WAIT).untilAsserted(() -> assertThat(couponRedemptionRepository.existsByOrderId(ORDER)).isFalse());
        assertThat(couponRepository.findById(couponId).orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void platformSettingsChanged_changesTheCartPricing() {
        send(KafkaTopics.PLATFORM_EVENTS, "settings", PlatformSettingsChanged.newBuilder()
                .setEventId(UUID.randomUUID().toString()).setOccurredAt(Instant.now())
                .setCurrency("INR").setDeliveryFee("25.00").setFreeDeliveryFrom("399.00").setMinimumOrderValue("149.00")
                .setChangedByUserId(1L)
                .build());

        await().atMost(WAIT).untilAsserted(() -> assertThat(pricingRulesProvider.current().minimumOrderValue()).isEqualByComparingTo("149.00"));
        assertThat(pricingRulesProvider.current().deliveryFee()).isEqualByComparingTo("25.00");
    }

    private void send(Object event) {
        send(KafkaTopics.ORDER_EVENTS, String.valueOf(ORDER), event);
    }

    private void send(String topic, String key, Object event) {
        KafkaAvroSerializer avroSerializer = new KafkaAvroSerializer();
        avroSerializer.configure(Map.of("schema.registry.url", "mock://grocery-tests",
                "value.subject.name.strategy", TopicRecordNameStrategy.class.getName()), false);
        try (Producer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()), new StringSerializer(), avroSerializer)) {
            producer.send(new ProducerRecord<>(topic, key, event));
        }
    }
}
