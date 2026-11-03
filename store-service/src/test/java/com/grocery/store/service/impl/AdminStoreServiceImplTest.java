package com.grocery.store.service.impl;

import com.grocery.events.store.StoreApproved;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.store.dto.request.ReasonRequest;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.entity.Store;
import com.grocery.store.enums.StoreStatus;
import com.grocery.store.exception.BusinessException;
import com.grocery.store.exception.ResourceNotFoundException;
import com.grocery.store.mapper.StoreEventMapper;
import com.grocery.store.mapper.StoreMapper;
import com.grocery.store.messaging.OutboxEventPublisher;
import com.grocery.store.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStoreServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private AdminStoreServiceImpl adminStoreService;

    @BeforeEach
    void setUp() {
        adminStoreService = new AdminStoreServiceImpl(
                storeRepository, new StoreMapper(Clock.systemUTC()), new StoreEventMapper(), outboxEventPublisher);
    }

    @Test
    void approveStore_activatesIt_andPublishesStoreApproved() {
        when(storeRepository.findById(3L)).thenReturn(Optional.of(store(StoreStatus.PENDING_APPROVAL)));

        StoreResponse response = adminStoreService.approveStore(3L, 2L);

        assertThat(response.status()).isEqualTo(StoreStatus.ACTIVE);
        ArgumentCaptor<StoreApproved> event = ArgumentCaptor.forClass(StoreApproved.class);
        verify(outboxEventPublisher).publish(eq("store"), eq(3L), event.capture());
        assertThat(event.getValue().getOwnerUserId()).isEqualTo(10L);
        assertThat(event.getValue().getApprovedByUserId()).isEqualTo(2L);
    }

    @Test
    void approveStore_fails_forAnActiveStore_andPublishesNothing() {
        when(storeRepository.findById(3L)).thenReturn(Optional.of(store(StoreStatus.ACTIVE)));

        assertThatThrownBy(() -> adminStoreService.approveStore(3L, 2L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void rejectStore_publishesStoreStatusChangedWithTheReason() {
        when(storeRepository.findById(3L)).thenReturn(Optional.of(store(StoreStatus.PENDING_APPROVAL)));

        adminStoreService.rejectStore(3L, new ReasonRequest("Address could not be verified"));

        ArgumentCaptor<StoreStatusChanged> event = ArgumentCaptor.forClass(StoreStatusChanged.class);
        verify(outboxEventPublisher).publish(eq("store"), eq(3L), event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(event.getValue().getReason()).isEqualTo("Address could not be verified");
    }

    @Test
    void getStore_fails_forAnUnknownId() {
        when(storeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminStoreService.getStore(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Store not found with id: 99");
        verify(storeRepository).findById(any());
    }

    private Store store(StoreStatus status) {
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
