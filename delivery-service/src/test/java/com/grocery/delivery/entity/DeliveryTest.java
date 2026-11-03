package com.grocery.delivery.entity;

import com.grocery.delivery.enums.DeliveryIssue;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.exception.BusinessException;
import com.grocery.delivery.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The delivery state machine (plain JUnit).
 */
class DeliveryTest {

    private static final Long PARTNER = 5L;
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    @Test
    void happyPath_assignedToDelivered() {
        Delivery delivery = newDelivery();

        delivery.assignTo(PARTNER, NOW);
        delivery.accept(PARTNER);
        delivery.pickUp(PARTNER, NOW);
        delivery.startTrip(PARTNER);
        delivery.deliver(PARTNER, new BigDecimal("40.00"), NOW);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getPartnerPayout()).isEqualByComparingTo("40.00");
        assertThat(delivery.getDeliveredAt()).isEqualTo(NOW);
    }

    @Test
    void eachNewStatusIsReportedOnce_forTheLiveTracking() {
        Delivery delivery = newDelivery();
        assertThat(delivery.takeStatusChange()).isTrue();    // new delivery: WAITING_FOR_PARTNER
        assertThat(delivery.takeStatusChange()).isFalse();   // nothing changed since

        delivery.assignTo(PARTNER, NOW);
        assertThat(delivery.takeStatusChange()).isTrue();
        delivery.setCoordinates(new BigDecimal("12.97"), new BigDecimal("77.64"), null, null);
        assertThat(delivery.takeStatusChange()).isFalse();   // other changes (coordinates, a photo, a rating) are not status changes
    }

    @Test
    void trackingRunsFromAcceptingUntilHandingOver() {
        assertThat(DeliveryStatus.ASSIGNED.isTracked()).isFalse();
        assertThat(DeliveryStatus.ACCEPTED.isTracked()).isTrue();
        assertThat(DeliveryStatus.PICKED_UP.isTracked()).isTrue();
        assertThat(DeliveryStatus.OUT_FOR_DELIVERY.isTracked()).isTrue();
        assertThat(DeliveryStatus.DELIVERED.isTracked()).isFalse();
        assertThat(DeliveryStatus.DELIVERED.isOver()).isTrue();
        assertThat(DeliveryStatus.FAILED.isOver()).isTrue();
        assertThat(DeliveryStatus.OUT_FOR_DELIVERY.isOver()).isFalse();
    }

    @Test
    void rejecting_putsItBackInTheQueue_andRemembersWhoSaidNo() {
        Delivery delivery = newDelivery();
        delivery.assignTo(PARTNER, NOW);

        delivery.reject(PARTNER);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.WAITING_FOR_PARTNER);
        assertThat(delivery.getPartnerUserId()).isNull();
        assertThat(delivery.wasRejectedBy(PARTNER)).isTrue();
    }

    @Test
    void anotherPartner_cannotTouchTheDelivery() {
        Delivery delivery = newDelivery();
        delivery.assignTo(PARTNER, NOW);

        assertThatThrownBy(() -> delivery.accept(8L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void stepsCannotBeSkipped() {
        Delivery delivery = newDelivery();
        delivery.assignTo(PARTNER, NOW);

        assertThatThrownBy(() -> delivery.pickUp(PARTNER, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is ASSIGNED, so it cannot become PICKED_UP");
    }

    @Test
    void failedDelivery_endsWithTheItemsBackAtTheStore() {
        Delivery delivery = newDelivery();
        delivery.assignTo(PARTNER, NOW);
        delivery.accept(PARTNER);
        delivery.pickUp(PARTNER, NOW);
        delivery.startTrip(PARTNER);

        delivery.reportIssue(PARTNER, DeliveryIssue.CUSTOMER_UNAVAILABLE, "Called twice, no answer");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getStatus().keepsPartnerBusy()).isTrue();

        delivery.markReturned(PARTNER);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.RETURNED);
        assertThat(delivery.getStatus().keepsPartnerBusy()).isFalse();
    }

    @Test
    void cancellingIsOnlyPossibleBeforePickup() {
        Delivery accepted = newDelivery();
        accepted.assignTo(PARTNER, NOW);
        accepted.accept(PARTNER);
        assertThat(accepted.canBeCancelled()).isTrue();

        accepted.pickUp(PARTNER, NOW);
        assertThat(accepted.canBeCancelled()).isFalse();
        accepted.failBecauseOrderWasCancelled();
        assertThat(accepted.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(accepted.getIssueNote()).contains("take the items back");
    }

    @Test
    void theCustomerRatesADeliveredOrder_once() {
        Delivery delivery = newDelivery();
        assertThatThrownBy(() -> delivery.rate(5, null, NOW)).hasMessageContaining("Only a delivered order can be rated");

        delivery.assignTo(PARTNER, NOW);
        delivery.accept(PARTNER);
        delivery.pickUp(PARTNER, NOW);
        delivery.startTrip(PARTNER);
        delivery.deliver(PARTNER, new BigDecimal("40.00"), NOW);
        delivery.rate(4, "Polite and on time", NOW);

        assertThat(delivery.getCustomerRating()).isEqualTo(4);
        assertThat(delivery.getCustomerComment()).isEqualTo("Polite and on time");
        assertThatThrownBy(() -> delivery.rate(5, null, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You have already rated this delivery");
    }

    private Delivery newDelivery() {
        Delivery delivery = Delivery.forOrder(10L, 4L, 1L, 1L);
        delivery.setPickup("Fresh Mart", "9000000003", "45, 100 Feet Road, Bengaluru 560038", "560038");
        delivery.setDrop("Priya Sharma", "9000000004", "12, 5th Cross, Bengaluru 560038", "560038");
        delivery.setPayment("COD", new BigDecimal("210.90"));
        return delivery;
    }
}
