package com.grocery.order.dto.response;

import com.grocery.order.enums.OrderStatus;

/**
 * What a vendor's live stream says when an order of the store changed (the "order" event), e.g.
 * {"orderId":194,"status":"CONFIRMED"}. Only which order and its status now: the page then loads what it shows with
 * the normal REST calls, so the lists, pages and counters have one source.
 */
public record OrderChange(Long orderId, OrderStatus status) {
}
