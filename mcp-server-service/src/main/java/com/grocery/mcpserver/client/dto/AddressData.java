package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One of the customer's delivery addresses (customer-service, GET /api/v1/customers/me/addresses), only the fields the
 * tools need (no phone number).
 *
 * @param label HOME, WORK or OTHER
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressData(Long id, String label, String contactName, String addressLine1, String addressLine2,
                          String landmark, String city, String pincode, boolean defaultAddress) {
}
