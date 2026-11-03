package com.grocery.customer.service;

import com.grocery.customer.dto.common.PageResponse;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.dto.response.CustomerResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Admin panel: look up customers.
 */
public interface AdminCustomerService {

    /** keyword is optional (null = all customers). */
    PageResponse<CustomerResponse> searchCustomers(String keyword, Pageable pageable);

    CustomerResponse getCustomer(Long customerId);

    List<AddressResponse> getCustomerAddresses(Long customerId);
}
