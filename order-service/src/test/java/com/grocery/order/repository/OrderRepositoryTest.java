package com.grocery.order.repository;

import com.grocery.order.TestcontainersConfiguration;
import com.grocery.order.config.JpaAuditingConfig;
import com.grocery.order.context.UserContext;
import com.grocery.order.context.UserContextHolder;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static com.grocery.order.support.TestOrders.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real MySQL (Testcontainers) + real Flyway migrations: also proves the entities match the schema.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class OrderRepositoryTest {

    private static final PageRequest NEWEST_FIRST = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void anOrderIsSavedWithItsItemsAndTimeline() {
        Order order = orderRepository.saveAndFlush(pendingOrder(PaymentMethod.COD));

        Order loaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(loaded.getItems()).hasSize(2);
        assertThat(loaded.getHistory()).hasSize(1);
        assertThat(loaded.getStore().getName()).isEqualTo("Fresh Mart");
        assertThat(loaded.getDeliveryAddress().getPincode()).isEqualTo("560038");
    }

    @Test
    void remembersWhoAddedTheOrderAndWhoChangedItLast() {
        UserContextHolder.set(new UserContext(4L, "CUSTOMER", null));   // as UserContextFilter does for a request
        Order order;
        try {
            order = orderRepository.saveAndFlush(pendingOrder(PaymentMethod.COD));
        } finally {
            UserContextHolder.clear();
        }
        assertThat(auditColumns(order.getId())).isEqualTo("CUSTOMER:4 / CUSTOMER:4");

        // A Kafka event (no logged-in user) confirms the order: the creator stays, the last change is the system's
        order.confirm(OrderPaymentStatus.CASH_ON_DELIVERY);
        orderRepository.saveAndFlush(order);
        assertThat(auditColumns(order.getId())).isEqualTo("CUSTOMER:4 / SYSTEM");
    }

    @Test
    void search_filtersByCustomerStoreAndStatus() {
        Order pending = orderRepository.save(pendingOrder(PaymentMethod.COD));
        Order confirmed = pendingOrder(PaymentMethod.COD);
        confirmed.confirm(OrderPaymentStatus.CASH_ON_DELIVERY);
        orderRepository.save(confirmed);

        assertThat(orderRepository.search(4L, null, null, NEWEST_FIRST).getContent())
                .extracting(Order::getId).containsExactly(confirmed.getId(), pending.getId());
        assertThat(orderRepository.search(null, 1L, OrderStatus.CONFIRMED, NEWEST_FIRST).getContent())
                .extracting(Order::getId).containsExactly(confirmed.getId());
        assertThat(orderRepository.search(999L, null, null, NEWEST_FIRST)).isEmpty();
    }

    @Test
    void customersAndStoresOnlyFindTheirOwnOrders() {
        Order order = orderRepository.save(pendingOrder(PaymentMethod.ONLINE));

        assertThat(orderRepository.findByIdAndCustomerUserId(order.getId(), 4L)).isPresent();
        assertThat(orderRepository.findByIdAndCustomerUserId(order.getId(), 7L)).isEmpty();
        assertThat(orderRepository.findByIdAndStoreId(order.getId(), 2L)).isEmpty();
    }

    @Test
    void anIdempotencyKeyIsUniquePerCustomer_andOrdersWithoutAKeyNeverClash() {
        Order first = pendingOrder(PaymentMethod.COD);
        first.placedWith("chat-card-0001", "fp-1");
        orderRepository.saveAndFlush(first);
        orderRepository.saveAndFlush(pendingOrder(PaymentMethod.COD));      // the website's orders have no key
        orderRepository.saveAndFlush(pendingOrder(PaymentMethod.COD));

        assertThat(orderRepository.findByCustomerUserIdAndIdempotencyKey(4L, "chat-card-0001")).get()
                .extracting(Order::getId).isEqualTo(first.getId());
        assertThat(orderRepository.findByCustomerUserIdAndIdempotencyKey(7L, "chat-card-0001")).isEmpty();

        Order sameKey = pendingOrder(PaymentMethod.COD);
        sameKey.placedWith("chat-card-0001", "fp-2");
        assertThatThrownBy(() -> orderRepository.saveAndFlush(sameKey)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theRecentOrdersOfACart_leaveOutCancelledOnesAndOlderOnes() {
        Order going = pendingOrder(PaymentMethod.COD);
        going.placedWith(null, "fp-cart");
        orderRepository.saveAndFlush(going);
        Order cancelled = pendingOrder(PaymentMethod.COD);
        cancelled.placedWith(null, "fp-cart");
        cancelled.cancel("Changed my mind", ChangedBy.CUSTOMER);
        orderRepository.saveAndFlush(cancelled);
        Instant aMinuteAgo = Instant.now().minus(Duration.ofMinutes(1));

        assertThat(orderRepository.findRecentOrdersOfCart(4L, "fp-cart", aMinuteAgo))
                .extracting(Order::getId).containsExactly(going.getId());
        assertThat(orderRepository.findRecentOrdersOfCart(4L, "fp-other-cart", aMinuteAgo)).isEmpty();
        assertThat(orderRepository.findRecentOrdersOfCart(4L, "fp-cart", Instant.now().plus(Duration.ofMinutes(1)))).isEmpty();
    }

    /** created_by and updated_by as stored in the database, for example "CUSTOMER:4 / SYSTEM". */
    private String auditColumns(Long orderId) {
        return jdbcClient.sql("SELECT CONCAT(created_by, ' / ', updated_by) FROM orders WHERE id = ?")
                .param(orderId)
                .query(String.class)
                .single();
    }
}
