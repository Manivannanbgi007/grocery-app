package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One delivery job (DeliveryResponse of delivery-service): a partner's own (GET /api/v1/delivery/deliveries) or one in
 * the admins' list (GET /api/v1/admin/deliveries). Only the fields the tools use: no phone numbers.
 *
 * @param status          WAITING_FOR_PARTNER, ASSIGNED, ACCEPTED, PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED,
 *                        RETURNED or CANCELLED
 * @param paymentMethod   COD or ONLINE
 * @param amountToCollect cash the partner collects (0 for an order paid online)
 * @param partnerPayout   what the partner earns for it; null until a partner has it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryData(long id, long orderId, String status, String storeName, String storeAddress,
                           String customerName, String customerAddress, String paymentMethod,
                           BigDecimal amountToCollect, BigDecimal partnerPayout, Instant createdAt) {
}
