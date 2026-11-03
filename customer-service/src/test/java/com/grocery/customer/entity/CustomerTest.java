package com.grocery.customer.entity;

import com.grocery.customer.enums.AddressLabel;
import com.grocery.customer.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The address rules of the Customer aggregate (plain JUnit - no Spring, no database).
 */
class CustomerTest {

    private final Customer customer = new Customer();

    @Test
    void firstAddress_becomesTheDefault() {
        Address home = address("Home street");

        customer.addAddress(home, false);

        assertThat(home.isDefaultAddress()).isTrue();
        assertThat(home.getCustomer()).isSameAs(customer);
    }

    @Test
    void makeDefault_leavesExactlyOneDefault() {
        Address home = address("Home street");
        Address work = address("Work street");
        customer.addAddress(home, false);
        customer.addAddress(work, false);

        customer.makeDefault(work);

        assertThat(work.isDefaultAddress()).isTrue();
        assertThat(home.isDefaultAddress()).isFalse();
    }

    @Test
    void removingTheDefault_makesTheOldestRemainingAddressTheDefault() {
        Address home = address("Home street");
        Address work = address("Work street");
        customer.addAddress(home, false);
        customer.addAddress(work, false);

        customer.removeAddress(home);

        assertThat(customer.getAddresses()).containsExactly(work);
        assertThat(work.isDefaultAddress()).isTrue();
    }

    @Test
    void cannotSaveMoreThanTenAddresses() {
        for (int i = 0; i < Customer.MAX_ADDRESSES; i++) {
            customer.addAddress(address("Street " + i), false);
        }

        assertThatThrownBy(() -> customer.addAddress(address("One too many"), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You can save at most 10 addresses");
    }

    private Address address(String line1) {
        Address address = new Address();
        address.setLabel(AddressLabel.HOME);
        address.setAddressLine1(line1);
        return address;
    }
}
