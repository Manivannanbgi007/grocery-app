package com.grocery.customer.service.impl;

import com.grocery.customer.dto.request.AddressRequest;
import com.grocery.customer.dto.request.UpdateProfileRequest;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.dto.response.CustomerResponse;
import com.grocery.customer.entity.Address;
import com.grocery.customer.entity.Customer;
import com.grocery.customer.exception.ResourceNotFoundException;
import com.grocery.customer.mapper.CustomerMapper;
import com.grocery.customer.repository.CustomerRepository;
import com.grocery.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getMyProfile(Long userId) {
        return customerMapper.toResponse(findCustomer(userId));
    }

    @Override
    @Transactional
    public CustomerResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        Customer customer = findCustomer(userId);
        customerMapper.copyProfile(request, customer);
        return customerMapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses(Long userId) {
        return findCustomer(userId).getAddresses().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest request) {
        Customer customer = findCustomer(userId);
        Address address = customerMapper.toAddress(request);
        customer.addAddress(address, request.wantsDefault());

        customerRepository.flush();   // write it now, so the new address gets its id for the response
        log.info("Customer id={} added address id={}", customer.getId(), address.getId());
        return customerMapper.toResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        Customer customer = findCustomer(userId);
        Address address = findAddress(customer, addressId);
        customerMapper.copyAddress(request, address);
        if (request.wantsDefault()) {
            customer.makeDefault(address);
        }
        return customerMapper.toResponse(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Customer customer = findCustomer(userId);
        customer.removeAddress(findAddress(customer, addressId));
        log.info("Customer id={} deleted address id={}", customer.getId(), addressId);
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(Long userId, Long addressId) {
        Customer customer = findCustomer(userId);
        Address address = findAddress(customer, addressId);
        customer.makeDefault(address);
        return customerMapper.toResponse(address);
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddressOfUser(Long userId, Long addressId) {
        return customerMapper.toResponse(findAddress(findCustomer(userId), addressId));
    }

    private Customer findCustomer(Long userId) {
        return customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer profile not found. It is created right after registration - please try again in a moment."));
    }

    /** Only finds addresses of THIS customer, so nobody can read or change someone else's address. */
    private Address findAddress(Customer customer, Long addressId) {
        return customer.findAddress(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }
}
