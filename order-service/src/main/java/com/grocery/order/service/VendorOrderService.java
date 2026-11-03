package com.grocery.order.service;

import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.enums.OrderStatus;
import org.springframework.data.domain.Pageable;

/**
 * Orders of the vendor's OWN store: accept or reject, pack, hand over. Every method takes the vendor's storeId.
 */
public interface VendorOrderService {

    PageResponse<OrderSummaryResponse> listStoreOrders(Long storeId, OrderStatus status, Pageable pageable);

    OrderResponse getStoreOrder(Long storeId, Long orderId);

    OrderResponse acceptOrder(Long storeId, Long orderId);

    OrderResponse rejectOrder(Long storeId, Long orderId, String reason);

    OrderResponse packOrder(Long storeId, Long orderId);

    OrderResponse markReadyForPickup(Long storeId, Long orderId);

    /** The invoice to print and put in the bag. */
    InvoiceFile getInvoice(Long storeId, Long orderId);
}
