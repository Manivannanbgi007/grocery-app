package com.grocery.order.service.impl;

import com.grocery.order.entity.ProcessedEvent;
import com.grocery.order.repository.ProcessedEventRepository;
import com.grocery.order.service.ProcessedEventService;
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
