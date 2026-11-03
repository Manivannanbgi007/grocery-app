package com.grocery.customer.service.impl;

import com.grocery.customer.dto.common.PageResponse;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.dto.response.CustomerResponse;
import com.grocery.customer.entity.Customer;
import com.grocery.customer.exception.ResourceNotFoundException;
import com.grocery.customer.mapper.CustomerMapper;
import com.grocery.customer.repository.CustomerRepository;
import com.grocery.customer.service.AdminCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCustomerServiceImpl implements AdminCustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Override
    public PageResponse<CustomerResponse> searchCustomers(String keyword, Pageable pageable) {
        String keywordFilter = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return PageResponse.from(customerRepository.search(keywordFilter, pageable).map(customerMapper::toResponse));
    }

    @Override
    public CustomerResponse getCustomer(Long customerId) {
        return customerMapper.toResponse(findCustomer(customerId));
    }

    @Override
    public List<AddressResponse> getCustomerAddresses(Long customerId) {
        return findCustomer(customerId).getAddresses().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }
}
