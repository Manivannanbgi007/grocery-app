package com.grocery.delivery.controller;

import com.grocery.delivery.dto.common.ApiResponse;
import com.grocery.delivery.dto.common.PageResponse;
import com.grocery.delivery.dto.request.AssignRequest;
import com.grocery.delivery.dto.request.ReasonRequest;
import com.grocery.delivery.dto.request.ZoneRequest;
import com.grocery.delivery.dto.response.DeliveryResponse;
import com.grocery.delivery.dto.response.PartnerResponse;
import com.grocery.delivery.dto.response.ZoneResponse;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.enums.KycStatus;
import com.grocery.delivery.service.AdminDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin panel: delivery partner KYC, delivery zones and charges, and every delivery.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Delivery", description = "Partner KYC, delivery zones, deliveries (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminDeliveryController {

    private final AdminDeliveryService adminDeliveryService;

    // ----- partners -----

    @Operation(summary = "Delivery partners, e.g. kycStatus=PENDING for the approval queue")
    @GetMapping("/delivery-partners")
    public ResponseEntity<ApiResponse<PageResponse<PartnerResponse>>> searchPartners(
            @RequestParam(required = false) KycStatus kycStatus,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Partners fetched successfully", adminDeliveryService.searchPartners(kycStatus, pageable)));
    }

    @Operation(summary = "Approve a partner's KYC")
    @PostMapping("/delivery-partners/{partnerUserId}/approve")
    public ResponseEntity<ApiResponse<PartnerResponse>> approveKyc(@PathVariable Long partnerUserId) {
        return ResponseEntity.ok(ApiResponse.success("KYC approved", adminDeliveryService.approveKyc(partnerUserId)));
    }

    @Operation(summary = "Reject a partner's KYC")
    @PostMapping("/delivery-partners/{partnerUserId}/reject")
    public ResponseEntity<ApiResponse<PartnerResponse>> rejectKyc(@PathVariable Long partnerUserId, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("KYC rejected", adminDeliveryService.rejectKyc(partnerUserId, request.reason())));
    }

    // ----- zones -----

    @Operation(summary = "All delivery zones")
    @GetMapping("/delivery-zones")
    public ResponseEntity<ApiResponse<List<ZoneResponse>>> listZones() {
        return ResponseEntity.ok(ApiResponse.success("Zones fetched successfully", adminDeliveryService.listZones()));
    }

    @Operation(summary = "Create a delivery zone (PIN codes + partner payout per delivery)")
    @PostMapping("/delivery-zones")
    public ResponseEntity<ApiResponse<ZoneResponse>> createZone(@Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Zone created", adminDeliveryService.createZone(request)));
    }

    @Operation(summary = "Update a delivery zone")
    @PutMapping("/delivery-zones/{zoneId}")
    public ResponseEntity<ApiResponse<ZoneResponse>> updateZone(@PathVariable Long zoneId, @Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Zone updated", adminDeliveryService.updateZone(zoneId, request)));
    }

    // ----- deliveries -----

    @Operation(summary = "All deliveries, e.g. status=WAITING_FOR_PARTNER")
    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<PageResponse<DeliveryResponse>>> searchDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) Long partnerUserId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Deliveries fetched successfully",
                adminDeliveryService.searchDeliveries(status, partnerUserId, pageable)));
    }

    @Operation(summary = "Assign a waiting delivery to a specific partner")
    @PostMapping("/deliveries/{deliveryId}/assign")
    public ResponseEntity<ApiResponse<DeliveryResponse>> assignManually(@PathVariable Long deliveryId, @Valid @RequestBody AssignRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Delivery assigned", adminDeliveryService.assignManually(deliveryId, request.partnerUserId())));
    }
}
