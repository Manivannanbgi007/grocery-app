package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.DeliveryData;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.PartnerData;
import com.grocery.mcpserver.client.dto.StoreDetailsData;
import com.grocery.mcpserver.tool.Formats;

import java.util.List;

/**
 * What getAdminWaitingWork returns: the work waiting for the platform team, by name (the first few of each kind).
 *
 * @param storesToApprove          how many stores wait for an approval in total
 * @param kycToReview              how many partners' KYC waits for a review in total
 * @param deliveriesWithoutPartner how many ready orders no partner took, in total
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitingWork(long storesToApprove, List<WaitingStore> stores,
                          long kycToReview, List<WaitingPartner> partners,
                          long deliveriesWithoutPartner, List<WaitingDelivery> deliveries) {

    /** @param registered when the vendor registered the store */
    public record WaitingStore(String store, String city, String registered) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WaitingPartner(String partner, String zone, String vehicle) {
    }

    /** @param waitingSince when the order became ready for pickup */
    public record WaitingDelivery(long orderNumber, String store, String waitingSince) {
    }

    public static WaitingWork from(PageData<StoreDetailsData> stores, PageData<PartnerData> partners,
                                   PageData<DeliveryData> deliveries) {
        return new WaitingWork(
                stores.totalElements(),
                stores.content().stream()
                        .map(store -> new WaitingStore(store.name(), store.city(), Formats.dateTime(store.createdAt())))
                        .toList(),
                partners.totalElements(),
                partners.content().stream()
                        .map(partner -> new WaitingPartner(partner.fullName(), partner.zoneName(), PanelTexts.vehicle(partner.vehicleType())))
                        .toList(),
                deliveries.totalElements(),
                deliveries.content().stream()
                        .map(delivery -> new WaitingDelivery(delivery.orderId(), delivery.storeName(), Formats.dateTime(delivery.createdAt())))
                        .toList());
    }
}
