package com.grocery.store.repository;

import com.grocery.store.TestcontainersConfiguration;
import com.grocery.store.config.JpaAuditingConfig;
import com.grocery.store.entity.OpeningHours;
import com.grocery.store.entity.Store;
import com.grocery.store.enums.StoreStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening hours are a time of day: 07:00 must be stored as 07:00 and read back as 07:00, whatever the time zone of the
 * machine. The test runs with the JVM in India time (like a developer PC), where the service used to shift every
 * opening time by 5:30 because of hibernate.jdbc.time_zone=UTC.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class OpeningHoursStorageTest {

    private static TimeZone originalTimeZone;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeAll
    static void runInIndiaTime() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void openingHours_areStoredAndReadWithoutTimeZoneShift() {
        Store store = store();
        store.replaceOpeningHours(List.of(OpeningHours.open(DayOfWeek.MONDAY, LocalTime.of(7, 0), LocalTime.of(22, 0))));
        Long storeId = storeRepository.saveAndFlush(store).getId();

        String storedInDatabase = jdbcClient
                .sql("SELECT CONCAT(TIME_FORMAT(opens_at, '%H:%i'), '-', TIME_FORMAT(closes_at, '%H:%i')) "
                        + "FROM store_opening_hours WHERE store_id = ?")
                .param(storeId)
                .query(String.class)
                .single();
        assertThat(storedInDatabase).isEqualTo("07:00-22:00");

        entityManager.clear();   // read it again from the database, not from Hibernate's first-level cache
        OpeningHours monday = storeRepository.findById(storeId).orElseThrow().getOpeningHours().getFirst();
        assertThat(monday.getOpensAt()).isEqualTo(LocalTime.of(7, 0));
        assertThat(monday.getClosesAt()).isEqualTo(LocalTime.of(22, 0));
    }

    private Store store() {
        Store store = new Store();
        store.setOwnerUserId(201L);
        store.setName("Time Zone Mart");
        store.setPhone("9845012345");
        store.setAddressLine1("Main Road");
        store.setCity("Bengaluru");
        store.setState("Karnataka");
        store.setPincode("560038");
        store.setStatus(StoreStatus.ACTIVE);
        store.setCommissionPercentage(new BigDecimal("10.00"));
        store.setAcceptingOrders(true);
        return store;
    }
}
