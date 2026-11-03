package com.grocery.delivery.assignment;

import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.enums.KycStatus;
import com.grocery.delivery.mapper.DeliveryEventMapper;
import com.grocery.delivery.messaging.OutboxEventPublisher;
import com.grocery.delivery.presence.PartnerPresenceService;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.events.delivery.DeliveryAssigned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The matching rule: first approved partner of the zone who is online, free and did not reject the delivery.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryPartnerRepository partnerRepository;
    @Mock
    private PartnerPresenceService presenceService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private DeliveryAssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        assignmentService = new DeliveryAssignmentService(deliveryRepository, partnerRepository, presenceService,
                new DeliveryEventMapper(), outboxEventPublisher, Clock.systemUTC());
    }

    @Test
    void skipsOfflineBusyAndRejectingPartners() {
        DeliveryPartner offline = partner(5L);
        DeliveryPartner busy = partner(6L);
        DeliveryPartner rejectedBefore = partner(7L);
        DeliveryPartner free = partner(8L);
        when(partnerRepository.findByZoneIdAndKycStatusOrderByIdAsc(1L, KycStatus.APPROVED))
                .thenReturn(List.of(offline, busy, rejectedBefore, free));
        when(presenceService.isOnline(5L)).thenReturn(false);
        lenient().when(presenceService.isOnline(6L)).thenReturn(true);
        lenient().when(presenceService.isOnline(8L)).thenReturn(true);
        when(deliveryRepository.existsByPartnerUserIdAndStatusIn(eq(6L), anyCollection())).thenReturn(true);
        when(deliveryRepository.existsByPartnerUserIdAndStatusIn(eq(8L), anyCollection())).thenReturn(false);

        Delivery delivery = waitingDelivery();
        delivery.assignTo(7L, Instant.now());
        delivery.reject(7L);

        assertThat(assignmentService.tryAssign(delivery)).isTrue();
        assertThat(delivery.getPartnerUserId()).isEqualTo(8L);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        verify(outboxEventPublisher).publish(eq("delivery"), eq(10L), any(DeliveryAssigned.class));
    }

    @Test
    void nobodyOnline_theDeliveryWaits() {
        when(partnerRepository.findByZoneIdAndKycStatusOrderByIdAsc(1L, KycStatus.APPROVED)).thenReturn(List.of(partner(5L)));
        when(presenceService.isOnline(5L)).thenReturn(false);

        Delivery delivery = waitingDelivery();

        assertThat(assignmentService.tryAssign(delivery)).isFalse();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.WAITING_FOR_PARTNER);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void deliveryWithoutAZone_isNeverAssignedAutomatically() {
        Delivery delivery = Delivery.forOrder(11L, 4L, 1L, null);

        assertThat(assignmentService.tryAssign(delivery)).isFalse();
        verifyNoInteractions(partnerRepository);
    }

    private Delivery waitingDelivery() {
        Delivery delivery = Delivery.forOrder(10L, 4L, 1L, 1L);
        delivery.setPickup("Fresh Mart", "9000000003", "45, 100 Feet Road", "560038");
        delivery.setDrop("Priya Sharma", "9000000004", "12, 5th Cross", "560038");
        delivery.setPayment("COD", new BigDecimal("210.90"));
        delivery.setId(100L);
        return delivery;
    }

    private DeliveryPartner partner(Long userId) {
        return DeliveryPartner.register(userId, "Partner " + userId, "98450000" + String.format("%02d", userId));
    }
}
