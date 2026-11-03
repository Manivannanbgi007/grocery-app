package com.grocery.store.mapper;

import com.grocery.store.dto.request.OpeningHoursRequest;
import com.grocery.store.dto.request.StoreDetailsRequest;
import com.grocery.store.dto.response.OpeningHoursResponse;
import com.grocery.store.dto.response.PublicStoreResponse;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.entity.OpeningHours;
import com.grocery.store.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Converts between Store entities and DTOs. Only copies fields - business decisions stay in the service.
 */
@Component
@RequiredArgsConstructor
public class StoreMapper {

    /** Store-local clock (see ClockConfig), used for the "openNow" flag. */
    private final Clock clock;

    public StoreResponse toResponse(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getOwnerUserId(),
                store.getName(),
                store.getDescription(),
                store.getPhone(),
                store.getEmail(),
                store.getAddressLine1(),
                store.getAddressLine2(),
                store.getCity(),
                store.getState(),
                store.getPincode(),
                store.getLatitude(),
                store.getLongitude(),
                store.getStatus(),
                store.getStatusReason(),
                store.getCommissionPercentage(),
                store.isAcceptingOrders(),
                isOpenNow(store),
                toOpeningHoursResponses(store.getOpeningHours()),
                store.getDeliveryPincodes().stream().sorted().toList(),
                store.getApprovedAt(),
                store.getCreatedAt());
    }

    public PublicStoreResponse toPublicResponse(Store store) {
        return new PublicStoreResponse(
                store.getId(),
                store.getName(),
                store.getDescription(),
                store.getPhone(),
                store.getAddressLine1(),
                store.getAddressLine2(),
                store.getCity(),
                store.getState(),
                store.getPincode(),
                store.getLatitude(),
                store.getLongitude(),
                store.isAcceptingOrders(),
                isOpenNow(store),
                toOpeningHoursResponses(store.getOpeningHours()));
    }

    public Store toEntity(StoreDetailsRequest request) {
        Store store = new Store();
        copyDetails(request, store);
        return store;
    }

    public void copyDetails(StoreDetailsRequest request, Store store) {
        store.setName(request.name().trim());
        store.setDescription(request.description());
        store.setPhone(request.phone());
        store.setEmail(request.email());
        store.setAddressLine1(request.addressLine1());
        store.setAddressLine2(request.addressLine2());
        store.setCity(request.city().trim());
        store.setState(request.state().trim());
        store.setPincode(request.pincode());
        store.setLatitude(request.latitude());
        store.setLongitude(request.longitude());
    }

    public OpeningHours toOpeningHours(OpeningHoursRequest request) {
        return request.closedAllDay()
                ? OpeningHours.closedAllDay(request.dayOfWeek())
                : OpeningHours.open(request.dayOfWeek(), request.opensAt(), request.closesAt());
    }

    private List<OpeningHoursResponse> toOpeningHoursResponses(List<OpeningHours> openingHours) {
        return openingHours.stream()
                .sorted(Comparator.comparing(OpeningHours::getDayOfWeek))   // MONDAY first
                .map(hours -> new OpeningHoursResponse(
                        hours.getDayOfWeek(), hours.getOpensAt(), hours.getClosesAt(), hours.isClosed()))
                .toList();
    }

    private boolean isOpenNow(Store store) {
        return store.isActive() && store.isOpenAt(LocalDateTime.now(clock));
    }
}
