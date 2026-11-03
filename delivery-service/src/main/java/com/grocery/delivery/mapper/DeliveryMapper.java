package com.grocery.delivery.mapper;

import com.grocery.delivery.dto.common.GeoPoint;
import com.grocery.delivery.dto.response.DeliveryResponse;
import com.grocery.delivery.dto.response.PartnerResponse;
import com.grocery.delivery.dto.response.ZoneResponse;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.entity.DeliveryZone;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMapper {

    public DeliveryResponse toResponse(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getStatus(),
                delivery.getStoreName(),
                delivery.getStorePhone(),
                delivery.getStoreAddress(),
                delivery.getCustomerName(),
                delivery.getCustomerPhone(),
                delivery.getCustomerAddress(),
                delivery.getPaymentMethod(),
                delivery.getAmountToCollect(),
                delivery.getPartnerUserId(),
                delivery.getIssue(),
                delivery.getIssueNote(),
                delivery.getProofPhotoUrl(),
                delivery.getPartnerPayout(),
                delivery.getCustomerRating(),
                delivery.getCustomerComment(),
                delivery.getAssignedAt(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt(),
                delivery.getCreatedAt(),
                GeoPoint.of(delivery.getPickupLatitude(), delivery.getPickupLongitude()),
                GeoPoint.of(delivery.getDropLatitude(), delivery.getDropLongitude()));
    }

    /** @param online from Redis (the entity does not know it) */
    public PartnerResponse toResponse(DeliveryPartner partner, boolean online) {
        DeliveryZone zone = partner.getZone();
        return new PartnerResponse(
                partner.getUserId(),
                partner.getFullName(),
                partner.getPhone(),
                partner.getVehicleType(),
                partner.getVehicleNumber(),
                partner.getDrivingLicenseNumber(),
                zone == null ? null : zone.getId(),
                zone == null ? null : zone.getName(),
                partner.getKycStatus(),
                partner.getKycNote(),
                online,
                partner.getRatingAverage(),
                partner.getRatingCount());
    }

    public ZoneResponse toResponse(DeliveryZone zone) {
        return new ZoneResponse(zone.getId(), zone.getName(), zone.getCity(),
                zone.getPincodes().stream().sorted().toList(), zone.getPartnerPayout(), zone.isActive());
    }
}
