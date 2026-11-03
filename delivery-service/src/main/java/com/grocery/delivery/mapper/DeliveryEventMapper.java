package com.grocery.delivery.mapper;

import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.events.delivery.DeliveryAssigned;
import com.grocery.events.delivery.DeliveryCompleted;
import com.grocery.events.delivery.DeliveryFailed;
import com.grocery.events.delivery.DeliveryPickedUp;
import com.grocery.events.delivery.PartnerKycReviewed;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds the Avro events this service publishes (key = orderId). Every event gets a new unique eventId.
 */
@Component
public class DeliveryEventMapper {

    public DeliveryAssigned toDeliveryAssigned(Delivery delivery, DeliveryPartner partner) {
        return DeliveryAssigned.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setDeliveryId(delivery.getId()).setOrderId(delivery.getOrderId())
                .setCustomerUserId(delivery.getCustomerUserId()).setStoreId(delivery.getStoreId())
                .setPartnerUserId(partner.getUserId())
                .setPartnerName(partner.getFullName())
                .setPartnerPhone(partner.getPhone())
                .build();
    }

    public DeliveryPickedUp toDeliveryPickedUp(Delivery delivery) {
        return DeliveryPickedUp.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setDeliveryId(delivery.getId()).setOrderId(delivery.getOrderId())
                .setCustomerUserId(delivery.getCustomerUserId()).setStoreId(delivery.getStoreId())
                .setPartnerUserId(delivery.getPartnerUserId())
                .build();
    }

    public DeliveryCompleted toDeliveryCompleted(Delivery delivery) {
        return DeliveryCompleted.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setDeliveryId(delivery.getId()).setOrderId(delivery.getOrderId())
                .setCustomerUserId(delivery.getCustomerUserId()).setStoreId(delivery.getStoreId())
                .setPartnerUserId(delivery.getPartnerUserId())
                .setAmountCollected(text(delivery.getAmountToCollect()))
                .setProofPhotoUrl(delivery.getProofPhotoUrl())
                .build();
    }

    public DeliveryFailed toDeliveryFailed(Delivery delivery) {
        return DeliveryFailed.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setDeliveryId(delivery.getId()).setOrderId(delivery.getOrderId())
                .setCustomerUserId(delivery.getCustomerUserId()).setStoreId(delivery.getStoreId())
                .setPartnerUserId(delivery.getPartnerUserId())
                .setIssue(delivery.getIssue().name())
                .setNote(delivery.getIssueNote())
                .build();
    }

    public PartnerKycReviewed toPartnerKycReviewed(DeliveryPartner partner) {
        return PartnerKycReviewed.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setPartnerUserId(partner.getUserId())
                .setFullName(partner.getFullName())
                .setStatus(partner.getKycStatus().name())
                .setNote(partner.getKycNote())
                .build();
    }

    private String text(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }
}
