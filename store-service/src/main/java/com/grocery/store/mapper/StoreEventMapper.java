package com.grocery.store.mapper;

import com.grocery.events.store.StoreApproved;
import com.grocery.events.store.StoreRegistered;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.store.entity.Store;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the Avro events this service publishes. Every event gets a new unique eventId.
 */
@Component
public class StoreEventMapper {

    public StoreRegistered toStoreRegistered(Store store) {
        return StoreRegistered.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setStoreId(store.getId())
                .setOwnerUserId(store.getOwnerUserId())
                .setStoreName(store.getName())
                .setCity(store.getCity())
                .build();
    }

    public StoreApproved toStoreApproved(Store store) {
        return StoreApproved.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setStoreId(store.getId())
                .setOwnerUserId(store.getOwnerUserId())
                .setStoreName(store.getName())
                .setApprovedByUserId(store.getApprovedByUserId())
                .build();
    }

    public StoreStatusChanged toStoreStatusChanged(Store store) {
        return StoreStatusChanged.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setStoreId(store.getId())
                .setOwnerUserId(store.getOwnerUserId())
                .setStatus(store.getStatus().name())
                .setReason(store.getStatusReason())
                .build();
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }
}
