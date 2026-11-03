package com.grocery.order.mapper;

import com.grocery.order.client.dto.CheckoutSummary;
import com.grocery.order.client.dto.CustomerAddress;
import com.grocery.order.client.dto.StoreInfo;
import com.grocery.order.dto.request.PlaceOrderRequest;
import com.grocery.order.dto.response.DeliveryAddressResponse;
import com.grocery.order.dto.response.OrderAmountsResponse;
import com.grocery.order.dto.response.OrderItemResponse;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.dto.response.TimelineEntryResponse;
import com.grocery.order.entity.DeliveryAddress;
import com.grocery.order.entity.Order;
import com.grocery.order.entity.OrderAmounts;
import com.grocery.order.entity.OrderItem;
import com.grocery.order.entity.StoreDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    /** The website's page of one order: an online order is paid there (its payment window opens on that page). */
    private static final String ORDER_PAGE = "/orders/";

    /** Builds a new PENDING order from what the other services returned. */
    public Order toNewOrder(Long customerUserId, CheckoutSummary checkout, CustomerAddress address, StoreInfo store,
                            PlaceOrderRequest request) {
        List<OrderItem> items = checkout.items().stream().map(this::toOrderItem).toList();
        CheckoutSummary.Totals totals = checkout.totals();
        OrderAmounts amounts = new OrderAmounts(totals.itemsTotal(), checkout.couponCode(), totals.couponDiscount(),
                totals.deliveryFee(), totals.taxIncluded(), totals.payableAmount());
        StoreDetails storeDetails = new StoreDetails(store.name(), store.phone(), store.oneLineAddress(), store.pincode(),
                store.commissionPercentage(), store.latitude(), store.longitude());
        DeliveryAddress deliveryAddress = new DeliveryAddress(address.contactName(), address.contactPhone(),
                address.addressLine1(), address.addressLine2(), address.landmark(), address.city(), address.state(),
                address.pincode(), address.latitude(), address.longitude());
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();

        return Order.place(customerUserId, store.id(), storeDetails, deliveryAddress, request.paymentMethod(), amounts, note, items);
    }

    public OrderResponse toResponse(Order order) {
        DeliveryAddress address = order.getDeliveryAddress();
        OrderAmounts amounts = order.getAmounts();
        return new OrderResponse(
                order.getId(),
                order.getCustomerUserId(),
                order.getStoreId(),
                order.getStore().getName(),
                order.getStore().getPhone(),
                order.getStore().getAddress(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.awaitsOnlinePayment() ? ORDER_PAGE + order.getId() : null,
                order.getItems().stream().map(this::toItemResponse).toList(),
                new DeliveryAddressResponse(address.getContactName(), address.getContactPhone(), address.getAddressLine1(),
                        address.getAddressLine2(), address.getLandmark(), address.getCity(), address.getState(), address.getPincode()),
                new OrderAmountsResponse(amounts.getItemsTotal(), amounts.getCouponCode(), amounts.getCouponDiscount(),
                        amounts.getDeliveryFee(), amounts.getTaxIncluded(), amounts.getTotalAmount()),
                order.getCustomerNote(),
                order.getStatusReason(),
                order.getCancelledBy(),
                order.isCancellable(),
                order.getDeliveryPartnerName(),
                order.getDeliveryPartnerPhone(),
                order.getHistory().stream()
                        .map(step -> new TimelineEntryResponse(step.getStatus(), step.getNote(), step.getChangedBy(), step.getChangedAt()))
                        .toList(),
                order.getCreatedAt(),
                order.getDeliveredAt());
    }

    public OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomerUserId(),
                order.getStoreId(),
                order.getStore().getName(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.itemCount(),
                order.getAmounts().getTotalAmount(),
                order.getCreatedAt());
    }

    private OrderItem toOrderItem(CheckoutSummary.Item checkoutItem) {
        OrderItem item = new OrderItem();
        item.setProductId(checkoutItem.productId());
        item.setProductName(checkoutItem.productName());
        item.setUnit(checkoutItem.unit());
        item.setUnitQuantity(checkoutItem.unitQuantity());
        item.setImageUrl(checkoutItem.imageUrl());
        item.setPrice(checkoutItem.price());
        item.setUnitPrice(checkoutItem.sellingPrice());
        item.setTaxPercentage(checkoutItem.taxPercentage());
        item.setQuantity(checkoutItem.quantity());
        item.setLineTotal(checkoutItem.lineTotal());
        return item;
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getUnit(),
                item.getUnitQuantity().stripTrailingZeros(), item.getImageUrl(), item.getPrice(), item.getUnitPrice(),
                item.getTaxPercentage(), item.getQuantity(), item.getLineTotal());
    }
}
