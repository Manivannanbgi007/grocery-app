package com.grocery.delivery.service;

import com.grocery.delivery.dto.common.PageResponse;
import com.grocery.delivery.dto.request.ZoneRequest;
import com.grocery.delivery.dto.response.DeliveryResponse;
import com.grocery.delivery.dto.response.PartnerResponse;
import com.grocery.delivery.dto.response.ZoneResponse;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.enums.KycStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Admin work: partner KYC, delivery zones and charges, watching and fixing deliveries.
 */
public interface AdminDeliveryService {

    /** kycStatus = null: every partner (PENDING = the approval queue). */
    PageResponse<PartnerResponse> searchPartners(KycStatus kycStatus, Pageable pageable);

    PartnerResponse approveKyc(Long partnerUserId);

    PartnerResponse rejectKyc(Long partnerUserId, String reason);

    List<ZoneResponse> listZones();

    ZoneResponse createZone(ZoneRequest request);

    ZoneResponse updateZone(Long zoneId, ZoneRequest request);

    /** Every filter is optional (WAITING_FOR_PARTNER = deliveries nobody took yet). */
    PageResponse<DeliveryResponse> searchDeliveries(DeliveryStatus status, Long partnerUserId, Pageable pageable);

    /** Assign a waiting delivery to a specific approved partner. */
    DeliveryResponse assignManually(Long deliveryId, Long partnerUserId);

    DeliveryResponse getDeliveryOfOrder(Long orderId);
}
