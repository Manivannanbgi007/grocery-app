package com.grocery.store.service.impl;

import com.grocery.store.dto.common.PageResponse;
import com.grocery.store.dto.response.PublicStoreResponse;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.enums.StoreStatus;
import com.grocery.store.exception.ResourceNotFoundException;
import com.grocery.store.mapper.StoreMapper;
import com.grocery.store.repository.StoreRepository;
import com.grocery.store.service.StoreQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryServiceImpl implements StoreQueryService {

    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;

    @Override
    public PageResponse<PublicStoreResponse> listActiveStores(String city, String pincode, Pageable pageable) {
        return PageResponse.from(storeRepository
                .findActive(emptyToNull(city), emptyToNull(pincode), pageable)
                .map(storeMapper::toPublicResponse));
    }

    @Override
    public PublicStoreResponse getActiveStore(Long storeId) {
        return storeRepository.findByIdAndStatus(storeId, StoreStatus.ACTIVE)
                .map(storeMapper::toPublicResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Store", storeId));
    }

    @Override
    public StoreResponse getStoreForService(Long storeId) {
        return storeRepository.findById(storeId)
                .map(storeMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Store", storeId));
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
