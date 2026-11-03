package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.AvailabilityData;
import com.grocery.mcpserver.client.dto.PartnerData;
import com.grocery.mcpserver.tool.Formats;

/**
 * What getPartnerStatus returns: whether the partner gets deliveries now, and why not.
 *
 * @param online      true while the partner gets new deliveries
 * @param onlineUntil when "online" runs out (the open panel renews it); null while offline
 * @param kyc         "Approved", "Sent, waiting for an admin's review", "Rejected", "Not sent yet"
 * @param kycNote     why the KYC was rejected
 * @param zone        the area the partner delivers in
 * @param rating      e.g. "4.5 stars from 12 ratings"; null before the first rating
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartnerStatus(String name, boolean online, String onlineUntil, String kyc, String kycNote, String zone,
                            String vehicle, String rating) {

    public static PartnerStatus from(PartnerData partner, AvailabilityData availability) {
        String rating = partner.ratingAverage() == null ? null
                : partner.ratingAverage().stripTrailingZeros().toPlainString() + " stars from " + partner.ratingCount()
                + (partner.ratingCount() == 1 ? " rating" : " ratings");
        return new PartnerStatus(partner.fullName(), availability.online(),
                availability.online() ? Formats.dateTime(availability.onlineUntil()) : null,
                PanelTexts.kycStatus(partner.kycStatus()), partner.kycNote(), partner.zoneName(),
                PanelTexts.vehicle(partner.vehicleType()), rating);
    }
}
