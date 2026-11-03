package com.grocery.order.controller;

import com.grocery.order.constant.HeaderNames;
import com.grocery.order.context.UserContextHolder;
import com.grocery.order.dto.common.ApiResponse;
import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.request.CancelOrderRequest;
import com.grocery.order.dto.request.PlaceOrderRequest;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.exception.BusinessException;
import com.grocery.order.service.CustomerOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * Customer panel: place, track and cancel MY orders (the customer comes from X-User-Id).
 * Allowed role (checked by the API gateway): CUSTOMER.
 */
@Tag(name = "Orders", description = "Place, track and cancel your orders (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final CustomerOrderService customerOrderService;

    @Operation(summary = "Place an order from my cart (status PENDING until stock and payment are confirmed)")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @Parameter(description = "Optional, 8-64 letters, digits, - or _: the same key for the same order. A repeat "
                    + "with the key (after a timeout or a double click) answers the order already placed")
            @RequestHeader(name = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        // Checked here and not with @Pattern: a constraint on a parameter would turn on method validation, and the body's
        // field errors would then lose their field names ("addressId")
        if (idempotencyKey != null && !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new BusinessException("The Idempotency-Key header must be 8-64 letters, digits, - or _");
        }
        OrderResponse order = customerOrderService.placeOrder(UserContextHolder.getRequiredUserId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed. We are reserving your items and confirming the payment.", order));
    }

    @Operation(summary = "My order history (optionally one status)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> listMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<OrderSummaryResponse> orders = customerOrderService.listMyOrders(UserContextHolder.getRequiredUserId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Orders fetched successfully", orders));
    }

    @Operation(summary = "One of my orders, with the tracking timeline")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrder(@PathVariable Long orderId) {
        OrderResponse order = customerOrderService.getMyOrder(UserContextHolder.getRequiredUserId(), orderId);
        return ResponseEntity.ok(ApiResponse.success("Order fetched successfully", order));
    }

    @Operation(summary = "The tax invoice as a PDF (once the store accepted the order)")
    @GetMapping(value = "/{orderId}/invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getInvoice(@PathVariable Long orderId) {
        return InvoiceFile.asDownload(customerOrderService.getMyInvoice(UserContextHolder.getRequiredUserId(), orderId));
    }

    @Operation(summary = "Cancel my order (possible until it leaves the store; a paid order is refunded)")
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelMyOrder(@PathVariable Long orderId,
                                                                    @Valid @RequestBody(required = false) CancelOrderRequest request) {
        OrderResponse order = customerOrderService.cancelMyOrder(UserContextHolder.getRequiredUserId(), orderId, request);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));
    }
}
