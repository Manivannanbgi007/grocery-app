package com.grocery.store.service.impl;

import com.grocery.events.store.StoreRegistered;
import com.grocery.store.config.StoreProperties;
import com.grocery.store.dto.request.OpeningHoursRequest;
import com.grocery.store.dto.request.StoreDetailsRequest;
import com.grocery.store.dto.request.UpdateOpeningHoursRequest;
import com.grocery.store.dto.response.OpeningHoursResponse;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.entity.Store;
import com.grocery.store.enums.StoreStatus;
import com.grocery.store.exception.DuplicateResourceException;
import com.grocery.store.exception.ResourceNotFoundException;
import com.grocery.store.mapper.StoreEventMapper;
import com.grocery.store.mapper.StoreMapper;
import com.grocery.store.messaging.OutboxEventPublisher;
import com.grocery.store.repository.StoreRepository;
import com.grocery.store.validator.OpeningHoursValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorStoreServiceImplTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    /** Monday 26 Oct 2026, 12:00 in India. */
    private static final Clock MONDAY_NOON = Clock.fixed(Instant.parse("2026-10-26T06:30:00Z"), INDIA);

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private VendorStoreServiceImpl vendorStoreService;

    @BeforeEach
    void setUp() {
        StoreProperties properties = new StoreProperties(INDIA, new BigDecimal("10.00"), LocalTime.of(7, 0), LocalTime.of(22, 0));
        vendorStoreService = new VendorStoreServiceImpl(storeRepository, new StoreMapper(MONDAY_NOON), new StoreEventMapper(),
                outboxEventPublisher, new OpeningHoursValidator(), properties);
    }

    @Test
    void registerStore_createsAPendingStoreWithDefaults_andPublishesStoreRegistered() {
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store store = invocation.getArgument(0);
            store.setId(3L);
            return store;
        });

        StoreResponse response = vendorStoreService.registerStore(10L, detailsRequest());

        assertThat(response.status()).isEqualTo(StoreStatus.PENDING_APPROVAL);
        assertThat(response.ownerUserId()).isEqualTo(10L);
        assertThat(response.commissionPercentage()).isEqualByComparingTo("10.00");
        assertThat(response.openingHours()).hasSize(7).allMatch(day -> day.opensAt().equals(LocalTime.of(7, 0)));
        assertThat(response.deliveryPincodes()).containsExactly("560038");
        assertThat(response.openNow()).isFalse();   // not ACTIVE yet

        ArgumentCaptor<StoreRegistered> event = ArgumentCaptor.forClass(StoreRegistered.class);
        verify(outboxEventPublisher).publish(eq("store"), eq(3L), event.capture());
        assertThat(event.getValue().getOwnerUserId()).isEqualTo(10L);
        assertThat(event.getValue().getStoreName()).isEqualTo("Daily Needs Supermarket");
    }

    @Test
    void registerStore_fails_whenTheVendorAlreadyHasAStore() {
        when(storeRepository.existsByOwnerUserId(3L)).thenReturn(true);

        assertThatThrownBy(() -> vendorStoreService.registerStore(3L, detailsRequest()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(storeRepository, never()).save(any());
    }

    @Test
    void getMyStore_fails_whenTheVendorHasNoStore() {
        when(storeRepository.findByOwnerUserId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorStoreService.getMyStore(10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("You have not registered a store yet");
    }

    @Test
    void updateMyStore_sendsARejectedStoreBackForApproval() {
        Store rejected = existingStore(StoreStatus.REJECTED);
        when(storeRepository.findByOwnerUserId(10L)).thenReturn(Optional.of(rejected));

        StoreResponse response = vendorStoreService.updateMyStore(10L, detailsRequest());

        assertThat(response.status()).isEqualTo(StoreStatus.PENDING_APPROVAL);
    }

    @Test
    void updateOpeningHours_marksDaysThatAreNotListedAsClosed() {
        Store store = existingStore(StoreStatus.ACTIVE);
        when(storeRepository.findByOwnerUserId(10L)).thenReturn(Optional.of(store));

        StoreResponse response = vendorStoreService.updateOpeningHours(10L, new UpdateOpeningHoursRequest(List.of(
                new OpeningHoursRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), false))));

        assertThat(response.openingHours()).hasSize(7);
        assertThat(response.openingHours().getFirst())
                .isEqualTo(new OpeningHoursResponse(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), false));
        assertThat(response.openingHours().subList(1, 7)).allMatch(OpeningHoursResponse::closed);
        assertThat(response.openNow()).isTrue();   // Monday 12:00 is within 09:00 - 18:00
    }

    private StoreDetailsRequest detailsRequest() {
        return new StoreDetailsRequest("Daily Needs Supermarket", "Groceries", "9845012345", "dailyneeds@example.com",
                "12, 5th Cross", null, "Bengaluru", "Karnataka", "560038", null, null);
    }

    private Store existingStore(StoreStatus status) {
        Store store = new Store();
        store.setId(3L);
        store.setOwnerUserId(10L);
        store.setName("Daily Needs");
        store.setCity("Bengaluru");
        store.setStatus(status);
        store.setCommissionPercentage(new BigDecimal("10.00"));
        return store;
    }
}
