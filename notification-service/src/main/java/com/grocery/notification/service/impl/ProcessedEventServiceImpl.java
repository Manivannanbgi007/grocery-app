package com.grocery.notification.service.impl;

import com.grocery.notification.entity.ProcessedEvent;
import com.grocery.notification.repository.ProcessedEventRepository;
import com.grocery.notification.service.ProcessedEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProcessedEventServiceImpl implements ProcessedEventService {

    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markAsProcessed(String eventId, String eventType) {
        if (processedEventRepository.existsById(eventId)) {
            return false;
        }
        processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
        return true;
    }
}
