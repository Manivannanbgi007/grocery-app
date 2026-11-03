package com.grocery.delivery.controller;

import com.grocery.delivery.context.UserContextHolder;
import com.grocery.delivery.dto.common.ApiResponse;
import com.grocery.delivery.dto.common.PageResponse;
import com.grocery.delivery.dto.request.IssueRequest;
import com.grocery.delivery.dto.response.DeliveryResponse;
import com.grocery.delivery.service.PartnerDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Delivery partner panel: MY delivery tasks and their status flow
 *   ASSIGNED -> accept (or reject) -> pick-up -> start -> deliver   (or: issue -> return)
 * Allowed role (checked by the API gateway): DELIVERY_PARTNER.
 */
@Tag(name = "Delivery partner - Deliveries", description = "Your delivery tasks and their status (DELIVERY_PARTNER)")
@RestController
@RequestMapping("/api/v1/delivery/deliveries")
@RequiredArgsConstructor
public class PartnerDeliveryController {

    private final PartnerDeliveryService partnerDeliveryService;

    @Operation(summary = "My deliveries: active=true for current tasks, false for completed ones")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DeliveryResponse>>> listMyDeliveries(
            @RequestParam(defaultValue = "true") boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Deliveries fetched successfully",
                partnerDeliveryService.listMyDeliveries(currentPartner(), active, pageable)));
    }

    @Operation(summary = "One of my deliveries, with the pickup and delivery addresses")
    @GetMapping("/{deliveryId}")
    public ResponseEntity<ApiResponse<DeliveryResponse>> getMyDelivery(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponse.success("Delivery fetched successfully",
                partnerDeliveryService.getMyDelivery(currentPartner(), deliveryId)));
    }

    @Operation(summary = "Accept an assigned delivery")
    @PostMapping("/{deliveryId}/accept")
    public ResponseEntity<ApiResponse<DeliveryResponse>> accept(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponse.success("Delivery accepted", partnerDeliveryService.accept(currentPartner(), deliveryId)));
    }

    @Operation(summary = "Reject an assigned delivery (it goes to another partner)")
    @PostMapping("/{deliveryId}/reject")
    public ResponseEntity<ApiResponse<DeliveryResponse>> reject(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponse.success("Delivery rejected", partnerDeliveryService.reject(currentPartner(), deliveryId)));
    }

    @Operation(summary = "I collected the order at the store")
    @PostMapping("/{deliveryId}/pick-up")
    public ResponseEntity<ApiResponse<DeliveryResponse>> pickUp(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponse.success("Order picked up", partnerDeliveryService.pickUp(currentPartner(), deliveryId)));
    }

    @Operation(summary = "I am on my way to the customer")
    @PostMapping("/{deliveryId}/start")
    public ResponseEntity<ApiResponse<DeliveryResponse>> startTrip(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponse.success("Out for delivery", partnerDeliveryService.startTrip(currentPartner(), deliveryId)));
    }

    @Operation(summary = "Delivered to the customer (cash collected for COD)")
    @PostMapping("/{deliveryId}/deliver")
    public ResponseEntity<ApiResponse<DeliveryResponse>> deliver(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponse.success("Delivered", partnerDeliveryService.deliver(currentPartner(), deliveryId)));
    }

    @Operation(summary = "Report a problem: customer unavailable, wrong address, items damaged ...")
    @PostMapping("/{deliveryId}/issue")
    public ResponseEntity<ApiResponse<DeliveryResponse>> reportIssue(@PathVariable Long deliveryId, @Valid @RequestBody IssueRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Issue reported", partnerDeliveryService.reportIssue(currentPartner(), deliveryId, request)));
    }

    @Operation(summary = "After a failed delivery: the items are back at the store")
    @PostMapping("/{deliveryId}/return")
    public ResponseEntity<ApiResponse<DeliveryResponse>> markReturned(@PathVariable Long deliveryId) {
        return ResponseEntity.ok(ApiResponse.success("Items returned to the store", partnerDeliveryService.markReturned(currentPartner(), deliveryId)));
    }

    @Operation(summary = "Upload a proof-of-delivery photo (JPEG, PNG or WEBP)")
    @PostMapping(value = "/{deliveryId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DeliveryResponse>> uploadProof(@PathVariable Long deliveryId, @RequestPart("photo") MultipartFile photo) {
        return ResponseEntity.ok(ApiResponse.success("Proof of delivery saved", partnerDeliveryService.uploadProof(currentPartner(), deliveryId, photo)));
    }

    private Long currentPartner() {
        return UserContextHolder.getRequiredUserId();
    }
}
