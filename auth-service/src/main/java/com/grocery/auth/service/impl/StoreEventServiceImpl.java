package com.grocery.auth.service.impl;

import com.grocery.auth.entity.User;
import com.grocery.auth.enums.Role;
import com.grocery.auth.repository.UserRepository;
import com.grocery.auth.service.ProcessedEventService;
import com.grocery.auth.service.StoreEventService;
import com.grocery.events.store.StoreApproved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreEventServiceImpl implements StoreEventService {

    private final UserRepository userRepository;
    private final ProcessedEventService processedEventService;

    @Override
    @Transactional
    public void handleStoreApproved(StoreApproved event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "StoreApproved")) {
            log.info("Skipping duplicate StoreApproved eventId={}", event.getEventId());
            return;
        }

        Optional<User> owner = userRepository.findById(event.getOwnerUserId());
        if (owner.isEmpty()) {
            log.warn("StoreApproved for unknown user id={} - ignored", event.getOwnerUserId());
            return;
        }
        User vendor = owner.get();
        if (vendor.getRole() != Role.VENDOR) {
            log.warn("StoreApproved for user id={} who is not a VENDOR - ignored", vendor.getId());
            return;
        }

        vendor.assignStore(event.getStoreId());
        log.info("Vendor id={} now owns store id={}", vendor.getId(), event.getStoreId());
    }
}
