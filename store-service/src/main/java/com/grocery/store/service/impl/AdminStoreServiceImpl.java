package com.grocery.store.service.impl;

import com.grocery.store.constant.KafkaTopics;
import com.grocery.store.dto.common.PageResponse;
import com.grocery.store.dto.request.ReasonRequest;
import com.grocery.store.dto.request.UpdateCommissionRequest;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.entity.Store;
import com.grocery.store.enums.StoreStatus;
import com.grocery.store.exception.ResourceNotFoundException;
import com.grocery.store.mapper.StoreEventMapper;
import com.grocery.store.mapper.StoreMapper;
import com.grocery.store.messaging.OutboxEventPublisher;
import com.grocery.store.repository.StoreRepository;
import com.grocery.store.service.AdminStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStoreServiceImpl implements AdminStoreService {

    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;
    private final StoreEventMapper storeEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> searchStores(StoreStatus status, String city, String keyword, Pageable pageable) {
        return PageResponse.from(storeRepository
                .search(status, emptyToNull(city), emptyToNull(keyword), pageable)
                .map(storeMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getStore(Long storeId) {
        return storeMapper.toResponse(findStore(storeId));
    }

    @Override
    @Transactional
    public StoreResponse approveStore(Long storeId, Long adminUserId) {
        Store store = findStore(storeId);
        store.approve(adminUserId, Instant.now());

        // auth-service listens for this and adds storeId to the vendor's next JWT
        outboxEventPublisher.publish(KafkaTopics.STORE_AGGREGATE, store.getId(), storeEventMapper.toStoreApproved(store));
        log.info("Admin id={} approved store id={}", adminUserId, storeId);
        return storeMapper.toResponse(store);
    }

    @Override
    @Transactional
    public StoreResponse rejectStore(Long storeId, ReasonRequest request) {
        Store store = findStore(storeId);
        store.reject(request.reason());
        return publishStatusChange(store);
    }

    @Override
    @Transactional
    public StoreResponse disableStore(Long storeId, ReasonRequest request) {
        Store store = findStore(storeId);
        store.disable(request.reason());
        return publishStatusChange(store);
    }

    @Override
    @Transactional
    public StoreResponse enableStore(Long storeId) {
        Store store = findStore(storeId);
        store.enable();
        return publishStatusChange(store);
    }

    @Override
    @Transactional
    public StoreResponse updateCommission(Long storeId, UpdateCommissionRequest request) {
        Store store = findStore(storeId);
        store.setCommissionPercentage(request.commissionPercentage());
        log.info("Store id={} commission set to {}%", storeId, request.commissionPercentage());
        return storeMapper.toResponse(store);
    }

    private StoreResponse publishStatusChange(Store store) {
        outboxEventPublisher.publish(KafkaTopics.STORE_AGGREGATE, store.getId(), storeEventMapper.toStoreStatusChanged(store));
        log.info("Store id={} is now {}", store.getId(), store.getStatus());
        return storeMapper.toResponse(store);
    }

    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store", storeId));
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
