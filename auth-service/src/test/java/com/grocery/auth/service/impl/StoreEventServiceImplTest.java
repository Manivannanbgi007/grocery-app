package com.grocery.auth.service.impl;

import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.enums.UserStatus;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.service.ProcessedEventService;
import com.grocery.events.store.StoreApproved;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreEventServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProcessedEventService processedEventService;

    @InjectMocks
    private StoreEventServiceImpl storeEventService;

    @Test
    void storeApproved_assignsTheStoreToTheVendor() {
        User vendor = user(10L, Role.VENDOR);
        when(processedEventService.markAsProcessed("event-1", "StoreApproved")).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(vendor));

        storeEventService.handleStoreApproved(storeApproved("event-1", 3L, 10L));

        assertThat(vendor.getStoreId()).isEqualTo(3L);
    }

    @Test
    void duplicateEvent_isSkipped() {
        when(processedEventService.markAsProcessed("event-1", "StoreApproved")).thenReturn(false);

        storeEventService.handleStoreApproved(storeApproved("event-1", 3L, 10L));

        verifyNoInteractions(userRepository);
    }

    @Test
    void storeApproved_forAUserWhoIsNotAVendor_isIgnored() {
        User customer = user(4L, Role.CUSTOMER);
        when(processedEventService.markAsProcessed("event-2", "StoreApproved")).thenReturn(true);
        when(userRepository.findById(4L)).thenReturn(Optional.of(customer));

        storeEventService.handleStoreApproved(storeApproved("event-2", 3L, 4L));

        assertThat(customer.getStoreId()).isNull();
    }

    private StoreApproved storeApproved(String eventId, long storeId, long ownerUserId) {
        return StoreApproved.newBuilder()
                .setEventId(eventId)
                .setOccurredAt(Instant.now())
                .setStoreId(storeId)
                .setOwnerUserId(ownerUserId)
                .setStoreName("Daily Needs")
                .setApprovedByUserId(2L)
                .build();
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
