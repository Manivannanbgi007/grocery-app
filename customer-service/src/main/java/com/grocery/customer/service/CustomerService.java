package com.grocery.customer.service;

import com.grocery.customer.dto.request.AddressRequest;
import com.grocery.customer.dto.request.UpdateProfileRequest;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.dto.response.CustomerResponse;

import java.util.List;

/**
 * Customer panel: a customer manages their own profile and delivery addresses.
 * The customer is identified by their auth-service user id (X-User-Id).
 */
public interface CustomerService {

    CustomerResponse getMyProfile(Long userId);

    CustomerResponse updateMyProfile(Long userId, UpdateProfileRequest request);

    List<AddressResponse> getMyAddresses(Long userId);

    AddressResponse addAddress(Long userId, AddressRequest request);

    AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request);

    void deleteAddress(Long userId, Long addressId);

    AddressResponse setDefaultAddress(Long userId, Long addressId);

    /** For other services (order-service copies the address into the order). Fails if the address is not the user's. */
    AddressResponse getAddressOfUser(Long userId, Long addressId);
}
