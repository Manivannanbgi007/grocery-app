package com.grocery.store.service.impl;

import com.grocery.store.config.StoreProperties;
import com.grocery.store.constant.KafkaTopics;
import com.grocery.store.dto.request.OpeningHoursRequest;
import com.grocery.store.dto.request.StoreDetailsRequest;
import com.grocery.store.dto.request.UpdateAcceptingOrdersRequest;
import com.grocery.store.dto.request.UpdateDeliveryAreasRequest;
import com.grocery.store.dto.request.UpdateOpeningHoursRequest;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.entity.OpeningHours;
import com.grocery.store.entity.Store;
import com.grocery.store.enums.StoreStatus;
import com.grocery.store.exception.DuplicateResourceException;
import com.grocery.store.exception.ResourceNotFoundException;
import com.grocery.store.mapper.StoreEventMapper;
import com.grocery.store.mapper.StoreMapper;
import com.grocery.store.messaging.OutboxEventPublisher;
import com.grocery.store.repository.StoreRepository;
import com.grocery.store.service.VendorStoreService;
import com.grocery.store.validator.OpeningHoursValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorStoreServiceImpl implements VendorStoreService {

    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;
    private final StoreEventMapper storeEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OpeningHoursValidator openingHoursValidator;
    private final StoreProperties storeProperties;

    @Override
    @Transactional
    public StoreResponse registerStore(Long ownerUserId, StoreDetailsRequest request) {
        if (storeRepository.existsByOwnerUserId(ownerUserId)) {
            throw new DuplicateResourceException("You already have a store. A vendor can own only one store.");
        }

        Store store = storeMapper.toEntity(request);
        store.setOwnerUserId(ownerUserId);
        store.setStatus(StoreStatus.PENDING_APPROVAL);
        store.setCommissionPercentage(storeProperties.defaultCommissionPercentage());
        store.setAcceptingOrders(true);
        store.replaceOpeningHours(defaultOpeningHours());
        store.replaceDeliveryPincodes(Set.of(request.pincode()));   // the store's own area to start with
        Store savedStore = storeRepository.save(store);

        outboxEventPublisher.publish(KafkaTopics.STORE_AGGREGATE, savedStore.getId(), storeEventMapper.toStoreRegistered(savedStore));
        log.info("Vendor id={} registered store id={}", ownerUserId, savedStore.getId());
        return storeMapper.toResponse(savedStore);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getMyStore(Long ownerUserId) {
        return storeMapper.toResponse(findMyStore(ownerUserId));
    }

    @Override
    @Transactional
    public StoreResponse updateMyStore(Long ownerUserId, StoreDetailsRequest request) {
        Store store = findMyStore(ownerUserId);
        storeMapper.copyDetails(request, store);
        store.resubmitIfRejected();
        log.info("Vendor id={} updated store id={} (status {})", ownerUserId, store.getId(), store.getStatus());
        return storeMapper.toResponse(store);
    }

    @Override
    @Transactional
    public StoreResponse updateOpeningHours(Long ownerUserId, UpdateOpeningHoursRequest request) {
        openingHoursValidator.validate(request.days());
        Store store = findMyStore(ownerUserId);

        // Days missing from the request are closed
        Map<DayOfWeek, OpeningHoursRequest> requestedDays = request.days().stream()
                .collect(Collectors.toMap(OpeningHoursRequest::dayOfWeek, Function.identity()));
        List<OpeningHours> week = Arrays.stream(DayOfWeek.values())
                .map(day -> requestedDays.containsKey(day)
                        ? storeMapper.toOpeningHours(requestedDays.get(day))
                        : OpeningHours.closedAllDay(day))
                .toList();

        store.replaceOpeningHours(week);
        return storeMapper.toResponse(store);
    }

    @Override
    @Transactional
    public StoreResponse updateDeliveryAreas(Long ownerUserId, UpdateDeliveryAreasRequest request) {
        Store store = findMyStore(ownerUserId);
        store.replaceDeliveryPincodes(request.pincodes());
        return storeMapper.toResponse(store);
    }

    @Override
    @Transactional
    public StoreResponse updateAcceptingOrders(Long ownerUserId, UpdateAcceptingOrdersRequest request) {
        Store store = findMyStore(ownerUserId);
        store.setAcceptingOrders(request.acceptingOrders());
        log.info("Store id={} acceptingOrders={}", store.getId(), request.acceptingOrders());
        return storeMapper.toResponse(store);
    }

    private Store findMyStore(Long ownerUserId) {
        return storeRepository.findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not registered a store yet"));
    }

    private List<OpeningHours> defaultOpeningHours() {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> OpeningHours.open(day, storeProperties.defaultOpeningTime(), storeProperties.defaultClosingTime()))
                .toList();
    }
}
