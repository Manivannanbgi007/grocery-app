package com.grocery.store.repository;

import com.grocery.store.TestcontainersConfiguration;
import com.grocery.store.config.JpaAuditingConfig;
import com.grocery.store.entity.Store;
import com.grocery.store.enums.StoreStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL (Testcontainers) + real Flyway migrations: also proves the entities match the schema.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class StoreRepositoryTest {

    @Autowired
    private StoreRepository storeRepository;

    @BeforeEach
    void setUp() {
        storeRepository.save(store(101L, "Fresh Mart", "Bengaluru", StoreStatus.ACTIVE, Set.of("560038", "560008")));
        storeRepository.save(store(102L, "Green Basket", "Bengaluru", StoreStatus.ACTIVE, Set.of("560034")));
        storeRepository.save(store(103L, "Chennai Fresh", "Chennai", StoreStatus.ACTIVE, Set.of("600001")));
        storeRepository.save(store(104L, "New Shop", "Bengaluru", StoreStatus.PENDING_APPROVAL, Set.of("560038")));
    }

    @Test
    void findActive_inACity_skipsStoresThatAreNotActive() {
        assertThat(storeRepository.findActive("bengaluru", null, PageRequest.of(0, 10)).getContent())
                .extracting(Store::getName)
                .containsExactlyInAnyOrder("Fresh Mart", "Green Basket");
    }

    @Test
    void findActive_byPincode_returnsOnlyStoresDeliveringThere() {
        assertThat(storeRepository.findActive(null, "560038", PageRequest.of(0, 10)).getContent())
                .extracting(Store::getName)
                .containsExactly("Fresh Mart");
    }

    @Test
    void search_byStatus_findsTheApprovalQueue() {
        assertThat(storeRepository.search(StoreStatus.PENDING_APPROVAL, null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Store::getName)
                .containsExactly("New Shop");
    }

    @Test
    void findByOwnerUserId_findsTheVendorsStore() {
        assertThat(storeRepository.findByOwnerUserId(102L)).get().extracting(Store::getName).isEqualTo("Green Basket");
    }

    private Store store(Long ownerUserId, String name, String city, StoreStatus status, Set<String> pincodes) {
        Store store = new Store();
        store.setOwnerUserId(ownerUserId);
        store.setName(name);
        store.setPhone("9845012345");
        store.setAddressLine1("Main Road");
        store.setCity(city);
        store.setState("State");
        store.setPincode(pincodes.iterator().next());
        store.setStatus(status);
        store.setCommissionPercentage(new BigDecimal("10.00"));
        store.setAcceptingOrders(true);
        store.replaceDeliveryPincodes(pincodes);
        return store;
    }
}
