package com.grocery.inventory.repository;

import com.grocery.inventory.TestcontainersConfiguration;
import com.grocery.inventory.config.JpaAuditingConfig;
import com.grocery.inventory.entity.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real MySQL (Testcontainers) + real Flyway migrations: also proves the entity matches the schema.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class StockRepositoryTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10, Sort.by("productId"));

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        stockRepository.save(Stock.create(101L, 1L, 50, 5));
        stockRepository.save(Stock.create(102L, 1L, 5, 5));    // low: exactly at the threshold
        Stock reservedDown = Stock.create(103L, 1L, 10, 5);
        reservedDown.reserve(6);                                 // low: 4 available
        stockRepository.save(reservedDown);
        stockRepository.save(Stock.create(201L, 2L, 0, 5));    // low (empty) but in store 2
    }

    @Test
    void search_forOneStore_returnsOnlyThatStoresStock() {
        assertThat(stockRepository.search(1L, false, FIRST_PAGE).getContent())
                .extracting(Stock::getProductId)
                .containsExactly(101L, 102L, 103L);
    }

    @Test
    void search_lowStockOnly_usesAvailableUnits_notUnitsOnHand() {
        assertThat(stockRepository.search(1L, true, FIRST_PAGE).getContent())
                .extracting(Stock::getProductId)
                .containsExactly(102L, 103L);
    }

    @Test
    void search_withoutStore_coversEveryStore() {
        assertThat(stockRepository.search(null, true, FIRST_PAGE).getContent())
                .extracting(Stock::getProductId)
                .containsExactly(102L, 103L, 201L);
    }

    @Test
    void findByProductIdAndStoreId_doesNotFindAnotherStoresProduct() {
        assertThat(stockRepository.findByProductIdAndStoreId(201L, 1L)).isEmpty();
        assertThat(stockRepository.findByProductIdAndStoreId(201L, 2L)).isPresent();
    }

    @Test
    void aProductCanHaveOnlyOneStockRecord() {
        assertThatThrownBy(() -> stockRepository.saveAndFlush(Stock.create(101L, 1L, 1, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
