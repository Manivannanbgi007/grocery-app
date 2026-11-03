package com.grocery.order.mapper;

import com.grocery.events.order.OrderAccepted;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderLine;
import com.grocery.events.order.OrderOutForDelivery;
import com.grocery.events.order.OrderPacked;
import com.grocery.events.order.OrderReadyForPickup;
import com.grocery.events.order.OrderRejected;
import com.grocery.order.entity.DeliveryAddress;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds the Avro events this service publishes. Every event gets a new unique eventId.
 * Amounts travel as decimal text ("201.00"): exact, and readable in Kafka UI.
 */
@Component
public class OrderEventMapper {

    public OrderCreated toOrderCreated(Order order) {
        return OrderCreated.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setOrderId(order.getId())
                .setCustomerUserId(order.getCustomerUserId())
                .setStoreId(order.getStoreId())
                .setItems(order.getItems().stream()
                        .map(item -> OrderLine.newBuilder()
                                .setProductId(item.getProductId())
                                .setProductName(item.getProductName())
                                .setQuantity(item.getQuantity())
                                .setUnitPrice(text(item.getUnitPrice()))
                                .build())
                        .toList())
                .setItemsTotal(text(order.getAmounts().getItemsTotal()))
                .setCouponCode(order.getAmounts().getCouponCode())
                .setCouponDiscount(text(order.getAmounts().getCouponDiscount()))
                .setDeliveryFee(text(order.getAmounts().getDeliveryFee()))
                .setTotalAmount(text(order.getAmounts().getTotalAmount()))
                .setPaymentMethod(order.getPaymentMethod().name())
                .setCommissionPercentage(order.getStore().getCommissionPercentage() == null
                        ? null : text(order.getStore().getCommissionPercentage()))
                .build();
    }

    public OrderConfirmed toOrderConfirmed(Order order) {
        return OrderConfirmed.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .setTotalAmount(text(order.getAmounts().getTotalAmount()))
                .setPaymentMethod(order.getPaymentMethod().name())
                .build();
    }

    public OrderCancelled toOrderCancelled(Order order) {
        return OrderCancelled.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .setReason(order.getStatusReason())
                .setCancelledBy(order.getCancelledBy().name())
                .build();
    }

    public OrderAccepted toOrderAccepted(Order order) {
        return OrderAccepted.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .build();
    }

    public OrderRejected toOrderRejected(Order order) {
        return OrderRejected.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .setReason(order.getStatusReason())
                .build();
    }

    public OrderPacked toOrderPacked(Order order) {
        return OrderPacked.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .build();
    }

    public OrderReadyForPickup toOrderReadyForPickup(Order order) {
        DeliveryAddress address = order.getDeliveryAddress();
        BigDecimal amountToCollect = order.getPaymentMethod() == PaymentMethod.COD
                ? order.getAmounts().getTotalAmount() : BigDecimal.ZERO;
        return OrderReadyForPickup.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .setStoreName(order.getStore().getName())
                .setStorePhone(order.getStore().getPhone())
                .setStoreAddress(order.getStore().getAddress())
                .setStorePincode(order.getStore().getPincode())
                .setDeliveryContactName(address.getContactName())
                .setDeliveryContactPhone(address.getContactPhone())
                .setDeliveryAddress(address.oneLine())
                .setDeliveryPincode(address.getPincode())
                .setPaymentMethod(order.getPaymentMethod().name())
                .setAmountToCollect(text(amountToCollect))
                // for the delivery tracking map; null when the store or the address has no coordinates
                .setStoreLatitude(toDouble(order.getStore().getLatitude()))
                .setStoreLongitude(toDouble(order.getStore().getLongitude()))
                .setDeliveryLatitude(toDouble(address.getLatitude()))
                .setDeliveryLongitude(toDouble(address.getLongitude()))
                .build();
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    public OrderOutForDelivery toOrderOutForDelivery(Order order) {
        return OrderOutForDelivery.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .build();
    }

    public OrderDelivered toOrderDelivered(Order order) {
        return OrderDelivered.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .setTotalAmount(text(order.getAmounts().getTotalAmount()))
                .setPaymentMethod(order.getPaymentMethod().name())
                .build();
    }

    public OrderDeliveryFailed toOrderDeliveryFailed(Order order) {
        return OrderDeliveryFailed.newBuilder()
                .setEventId(newEventId()).setOccurredAt(Instant.now())
                .setOrderId(order.getId()).setCustomerUserId(order.getCustomerUserId()).setStoreId(order.getStoreId())
                .setReason(order.getStatusReason())
                .build();
    }

    /** 201 -> "201.00" */
    private String text(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }
}
