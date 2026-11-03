package com.grocery.customer.entity;

import com.grocery.customer.enums.Gender;
import com.grocery.customer.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A shopper's profile. Created automatically from the UserRegistered event when someone
 * registers as a CUSTOMER (login details stay in auth-service).
 *
 * The customer owns their addresses: all address rules (maximum count, exactly one default)
 * live here, so they hold no matter which service method changes the addresses.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {

    public static final int MAX_ADDRESSES = 10;

    /** The auth-service user id of this customer. */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Copied from auth-service at registration (login email is changed there). */
    @Column(nullable = false, length = 150)
    private String email;

    /** Contact number for deliveries. */
    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Address> addresses = new ArrayList<>();

    // ----- address rules -----

    public Optional<Address> findAddress(Long addressId) {
        return addresses.stream()
                .filter(address -> Objects.equals(address.getId(), addressId))
                .findFirst();
    }

    /** Adds an address. The first address always becomes the default one. */
    public void addAddress(Address address, boolean makeDefault) {
        if (addresses.size() >= MAX_ADDRESSES) {
            throw new BusinessException("You can save at most " + MAX_ADDRESSES + " addresses");
        }
        address.setCustomer(this);
        addresses.add(address);
        if (makeDefault || addresses.size() == 1) {
            makeDefault(address);
        }
    }

    /** Removes an address. If it was the default, the oldest remaining address becomes the default. */
    public void removeAddress(Address address) {
        addresses.remove(address);
        if (address.isDefaultAddress() && !addresses.isEmpty()) {
            addresses.getFirst().setDefaultAddress(true);
        }
    }

    /** Exactly one address is the default. */
    public void makeDefault(Address newDefault) {
        addresses.forEach(address -> address.setDefaultAddress(address == newDefault));
    }
}
