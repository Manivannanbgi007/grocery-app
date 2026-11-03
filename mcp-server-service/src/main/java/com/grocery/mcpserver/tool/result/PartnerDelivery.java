package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.DeliveryData;
import com.grocery.mcpserver.tool.Formats;

/**
 * One of the partner's current deliveries (getPartnerDeliveries). No phone numbers: the panel has the call buttons.
 *
 * @param orderNumber   the number on the order ("Order #36")
 * @param step          "Given to the partner, not accepted yet", "Picked up at the store" ...
 * @param cashToCollect cash to take from the customer, or "Nothing (paid online)"
 * @param payout        what the partner earns for this delivery
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartnerDelivery(long orderNumber, String step, String store, String storeAddress, String customer,
                              String customerAddress, String cashToCollect, String payout) {

    public static PartnerDelivery from(DeliveryData delivery) {
        boolean cash = "COD".equals(delivery.paymentMethod())
                && delivery.amountToCollect() != null && delivery.amountToCollect().signum() > 0;
        return new PartnerDelivery(delivery.orderId(), PanelTexts.deliveryStep(delivery.status()), delivery.storeName(),
                delivery.storeAddress(), delivery.customerName(), delivery.customerAddress(),
                cash ? Formats.rupees(delivery.amountToCollect()) : "Nothing (paid online)",
                Formats.rupees(delivery.partnerPayout()));
    }
}
