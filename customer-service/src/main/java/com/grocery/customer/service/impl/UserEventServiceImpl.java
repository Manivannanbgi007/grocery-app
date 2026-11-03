package com.grocery.customer.service.impl;

import com.grocery.customer.entity.Customer;
import com.grocery.customer.mapper.CustomerMapper;
import com.grocery.customer.repository.CustomerRepository;
import com.grocery.customer.service.ProcessedEventService;
import com.grocery.customer.service.UserEventService;
import com.grocery.events.user.UserRegistered;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventServiceImpl implements UserEventService {

    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final ProcessedEventService processedEventService;

    @Override
    @Transactional
    public void handleUserRegistered(UserRegistered event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "UserRegistered")) {
            log.info("Skipping duplicate UserRegistered eventId={}", event.getEventId());
            return;
        }
        if (!CUSTOMER_ROLE.equals(event.getRole())) {
            log.debug("User id={} registered as {} - no customer profile needed", event.getUserId(), event.getRole());
            return;
        }
        if (customerRepository.existsByUserId(event.getUserId())) {
            log.info("Customer profile for user id={} already exists", event.getUserId());
            return;
        }

        Customer customer = customerRepository.save(customerMapper.toNewCustomer(event));
        log.info("Created customer id={} for user id={}", customer.getId(), event.getUserId());
    }
}
