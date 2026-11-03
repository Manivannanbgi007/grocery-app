package com.grocery.customer.mapper;

import com.grocery.customer.dto.request.AddressRequest;
import com.grocery.customer.dto.request.UpdateProfileRequest;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.dto.response.CustomerResponse;
import com.grocery.customer.entity.Address;
import com.grocery.customer.entity.Customer;
import com.grocery.events.user.UserRegistered;
import org.springframework.stereotype.Component;

/**
 * Converts between customer entities, DTOs and events. Only copies fields - business rules stay
 * in the Customer entity and the services.
 */
@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getUserId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getDateOfBirth(),
                customer.getGender(),
                customer.getAddresses().size(),
                customer.getCreatedAt());
    }

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getContactName(),
                address.getContactPhone(),
                address.getAddressLine1(),
                address.getAddressLine2(),
                address.getLandmark(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.getLatitude(),
                address.getLongitude(),
                address.isDefaultAddress());
    }

    /** A new profile from the registration event published by auth-service. */
    public Customer toNewCustomer(UserRegistered event) {
        Customer customer = new Customer();
        customer.setUserId(event.getUserId());
        customer.setFullName(event.getFullName());
        customer.setEmail(event.getEmail());
        customer.setPhone(event.getPhone());
        return customer;
    }

    public void copyProfile(UpdateProfileRequest request, Customer customer) {
        customer.setFullName(request.fullName().trim());
        customer.setPhone(request.phone());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setGender(request.gender());
    }

    public Address toAddress(AddressRequest request) {
        Address address = new Address();
        copyAddress(request, address);
        return address;
    }

    public void copyAddress(AddressRequest request, Address address) {
        address.setLabel(request.label());
        address.setContactName(request.contactName().trim());
        address.setContactPhone(request.contactPhone());
        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setLandmark(request.landmark());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPincode(request.pincode());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
    }
}
