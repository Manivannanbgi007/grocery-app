package com.grocery.order.dto.response;

import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;

import java.time.Instant;
import java.util.List;

/**
 * Everything about one order, including the tracking timeline.
 *
 * @param paymentUrl   where the customer pays an online order that is not paid yet: the website's page of the order
 *                     ("/orders/12", where the payment window opens). A path, not a full address, so it works on
 *                     every address the website runs on. Null when nothing is to be paid online (cash on delivery,
 *                     already paid, payment failed, or the order was cancelled).
 * @param statusReason why it was cancelled or rejected
 * @param cancellable  true while the customer may still cancel (before it leaves the store)
 * @param deliveryPartnerName who delivers it (null until a partner is assigned)
 */
public record OrderResponse(
        Long id,
        Long customerUserId,
        Long storeId,
        String storeName,
        String storePhone,
        String storeAddress,
        OrderStatus status,
        PaymentMethod paymentMethod,
        OrderPaymentStatus paymentStatus,
        String paymentUrl,
        List<OrderItemResponse> items,
        DeliveryAddressResponse deliveryAddress,
        OrderAmountsResponse amounts,
        String customerNote,
        String statusReason,
        ChangedBy cancelledBy,
        boolean cancellable,
        String deliveryPartnerName,
        String deliveryPartnerPhone,
        List<TimelineEntryResponse> timeline,
        Instant placedAt,
        Instant deliveredAt) {
}
