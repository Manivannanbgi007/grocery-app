package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.AvailabilityData;
import com.grocery.mcpserver.client.dto.DeliveryData;
import com.grocery.mcpserver.client.dto.EarningsData;
import com.grocery.mcpserver.client.dto.GatewayResponse;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.PartnerData;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The logged-in delivery partner's own data through the API gateway (delivery-service): profile and KYC, online or
 * offline, current deliveries, earnings.
 *
 * Every call carries the partner's token, so these calls can only return that partner's own data, exactly like the
 * delivery panel. Nothing is cached: deliveries and "online" change all the time.
 */
@Component
@RequiredArgsConstructor
public class PartnerClient {

    private static final ParameterizedTypeReference<GatewayResponse<PartnerData>> PROFILE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<AvailabilityData>> AVAILABILITY = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<DeliveryData>>> DELIVERY_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<EarningsData>> EARNINGS = new ParameterizedTypeReference<>() { };

    private final RestClient gatewayRestClient;

    /** GET /api/v1/delivery/profile; empty for a moment after signing up, while delivery-service creates the profile (404). */
    public Optional<PartnerData> profile(String partnerToken) {
        return GatewayCall.optionalDataOf("The partner profile", () -> gatewayRestClient.get()
                .uri("/api/v1/delivery/profile")
                .headers(headers -> headers.setBearerAuth(partnerToken))
                .retrieve()
                .body(PROFILE));
    }

    /** GET /api/v1/delivery/availability */
    public AvailabilityData availability(String partnerToken) {
        return GatewayCall.dataOf("The online status", () -> gatewayRestClient.get()
                .uri("/api/v1/delivery/availability")
                .headers(headers -> headers.setBearerAuth(partnerToken))
                .retrieve()
                .body(AVAILABILITY));
    }

    /** GET /api/v1/delivery/deliveries?active=true: the deliveries not finished yet, newest first. */
    public PageData<DeliveryData> activeDeliveries(String partnerToken, int size) {
        return GatewayCall.dataOf("The deliveries", () -> gatewayRestClient.get()
                .uri("/api/v1/delivery/deliveries?active=true&page=0&size={size}", size)
                .headers(headers -> headers.setBearerAuth(partnerToken))
                .retrieve()
                .body(DELIVERY_PAGE));
    }

    /** GET /api/v1/delivery/earnings: both dates included (India dates). */
    public EarningsData earnings(String partnerToken, LocalDate from, LocalDate to) {
        return GatewayCall.dataOf("The earnings", () -> gatewayRestClient.get()
                .uri("/api/v1/delivery/earnings?from={from}&to={to}", from, to)
                .headers(headers -> headers.setBearerAuth(partnerToken))
                .retrieve()
                .body(EARNINGS));
    }
}
