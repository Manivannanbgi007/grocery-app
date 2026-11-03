package com.grocery.customer.service.impl;

import com.grocery.customer.entity.Customer;
import com.grocery.customer.mapper.CustomerMapper;
import com.grocery.customer.repository.CustomerRepository;
import com.grocery.customer.service.ProcessedEventService;
import com.grocery.events.user.UserRegistered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEventServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProcessedEventService processedEventService;

    private UserEventServiceImpl userEventService;

    @BeforeEach
    void setUp() {
        userEventService = new UserEventServiceImpl(customerRepository, new CustomerMapper(), processedEventService);
    }

    @Test
    void newCustomer_getsAProfile() {
        when(processedEventService.markAsProcessed("event-1", "UserRegistered")).thenReturn(true);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userEventService.handleUserRegistered(userRegistered("event-1", 21L, "CUSTOMER"));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(21L);
        assertThat(saved.getValue().getEmail()).isEqualTo("new.user@example.com");
    }

    @Test
    void vendorRegistration_doesNotCreateACustomerProfile() {
        when(processedEventService.markAsProcessed("event-2", "UserRegistered")).thenReturn(true);

        userEventService.handleUserRegistered(userRegistered("event-2", 22L, "VENDOR"));

        verifyNoInteractions(customerRepository);
    }

    @Test
    void duplicateEvent_isSkipped() {
        when(processedEventService.markAsProcessed("event-1", "UserRegistered")).thenReturn(false);

        userEventService.handleUserRegistered(userRegistered("event-1", 21L, "CUSTOMER"));

        verifyNoInteractions(customerRepository);
    }

    @Test
    void existingProfile_isNotCreatedTwice() {
        when(processedEventService.markAsProcessed("event-3", "UserRegistered")).thenReturn(true);
        when(customerRepository.existsByUserId(4L)).thenReturn(true);

        userEventService.handleUserRegistered(userRegistered("event-3", 4L, "CUSTOMER"));

        verify(customerRepository, never()).save(any());
    }

    private UserRegistered userRegistered(String eventId, long userId, String role) {
        return UserRegistered.newBuilder()
                .setEventId(eventId)
                .setOccurredAt(Instant.now())
                .setUserId(userId)
                .setFullName("New User")
                .setEmail("new.user@example.com")
                .setPhone("9876543210")
                .setRole(role)
                .build();
    }
}
