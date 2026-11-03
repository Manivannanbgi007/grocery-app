package com.grocery.customer.service.impl;

import com.grocery.customer.dto.request.AddressRequest;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.entity.Address;
import com.grocery.customer.entity.Customer;
import com.grocery.customer.enums.AddressLabel;
import com.grocery.customer.exception.ResourceNotFoundException;
import com.grocery.customer.mapper.CustomerMapper;
import com.grocery.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    private CustomerServiceImpl customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(customerRepository, new CustomerMapper());
    }

    @Test
    void getMyProfile_explainsThatAProfileMayNotExistYet() {
        when(customerRepository.findByUserId(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getMyProfile(4L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("created right after registration");
    }

    @Test
    void addAddress_withSetAsDefault_movesTheDefault() {
        Customer customer = customerWithHomeAddress();
        when(customerRepository.findByUserId(4L)).thenReturn(Optional.of(customer));

        AddressResponse work = customerService.addAddress(4L, addressRequest(AddressLabel.WORK, true));

        assertThat(work.defaultAddress()).isTrue();
        assertThat(customer.getAddresses().getFirst().isDefaultAddress()).isFalse();
    }

    @Test
    void addressOfAnotherCustomer_isReportedAsNotFound() {
        when(customerRepository.findByUserId(4L)).thenReturn(Optional.of(customerWithHomeAddress()));

        assertThatThrownBy(() -> customerService.getAddressOfUser(4L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Address not found with id: 999");
    }

    private Customer customerWithHomeAddress() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setUserId(4L);
        Address home = new CustomerMapper().toAddress(addressRequest(AddressLabel.HOME, false));
        home.setId(1L);
        customer.addAddress(home, false);
        return customer;
    }

    private AddressRequest addressRequest(AddressLabel label, boolean setAsDefault) {
        return new AddressRequest(label, "Priya Sharma", "9000000004", "Flat 302", null, null,
                "Bengaluru", "Karnataka", "560038", null, null, setAsDefault);
    }
}
