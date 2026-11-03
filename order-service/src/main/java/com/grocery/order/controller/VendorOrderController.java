package com.grocery.order.controller;

import com.grocery.order.context.UserContextHolder;
import com.grocery.order.dto.common.ApiResponse;
import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.request.ReasonRequest;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.live.VendorOrderStreams;
import com.grocery.order.service.VendorOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Store panel: the orders of MY store (store id from X-Store-Id).
 *   new order (CONFIRMED) -> accept or reject -> pack -> ready for pickup
 * The panel follows them live with GET /stream (Server-Sent Events, see VendorOrderStreams).
 * Allowed role (checked by the API gateway): VENDOR.
 */
@Tag(name = "Vendor - Orders", description = "Your store's orders: accept, reject, pack, hand over (VENDOR)")
@RestController
@RequestMapping("/api/v1/vendor/orders")
@RequiredArgsConstructor
public class VendorOrderController {

    private final VendorOrderService vendorOrderService;
    private final VendorOrderStreams vendorOrderStreams;

    @Operation(summary = "My store's orders, e.g. status=CONFIRMED for the new ones waiting for me")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> listStoreOrders(
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<OrderSummaryResponse> orders = vendorOrderService.listStoreOrders(UserContextHolder.getRequiredStoreId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Orders fetched successfully", orders));
    }

    @Operation(summary = "Live updates of my store's orders (Server-Sent Events): \"ready\", then \"order\" after every change")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamStoreOrders() {
        Long storeId = UserContextHolder.getRequiredStoreId();
        if (vendorOrderStreams.isFull()) {
            // This instance has as many streams open as it may: the browser tries again in a moment
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")     // nginx and similar proxies: pass every event on at once
                .body(vendorOrderStreams.open(storeId));
    }

    @Operation(summary = "One order of my store")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getStoreOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order fetched successfully",
                vendorOrderService.getStoreOrder(UserContextHolder.getRequiredStoreId(), orderId)));
    }

    @Operation(summary = "The tax invoice as a PDF (once the store accepted the order)")
    @GetMapping(value = "/{orderId}/invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getInvoice(@PathVariable Long orderId) {
        return InvoiceFile.asDownload(vendorOrderService.getInvoice(UserContextHolder.getRequiredStoreId(), orderId));
    }

    @Operation(summary = "Accept a confirmed order")
    @PostMapping("/{orderId}/accept")
    public ResponseEntity<ApiResponse<OrderResponse>> acceptOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order accepted",
                vendorOrderService.acceptOrder(UserContextHolder.getRequiredStoreId(), orderId)));
    }

    @Operation(summary = "Reject a confirmed order (stock is released and a paid order is refunded)")
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectOrder(@PathVariable Long orderId,
                                                                  @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Order rejected",
                vendorOrderService.rejectOrder(UserContextHolder.getRequiredStoreId(), orderId, request.reason())));
    }

    @Operation(summary = "The items are packed")
    @PostMapping("/{orderId}/pack")
    public ResponseEntity<ApiResponse<OrderResponse>> packOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order packed",
                vendorOrderService.packOrder(UserContextHolder.getRequiredStoreId(), orderId)));
    }

    @Operation(summary = "The order is ready for the delivery partner")
    @PostMapping("/{orderId}/ready-for-pickup")
    public ResponseEntity<ApiResponse<OrderResponse>> markReadyForPickup(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order is ready for pickup",
                vendorOrderService.markReadyForPickup(UserContextHolder.getRequiredStoreId(), orderId)));
    }
}
