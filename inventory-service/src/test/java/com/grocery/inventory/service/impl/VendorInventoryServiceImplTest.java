package com.grocery.inventory.service.impl;

import com.grocery.events.inventory.LowStockDetected;
import com.grocery.events.inventory.StockChanged;
import com.grocery.inventory.client.ProductCatalogClient;
import com.grocery.inventory.client.dto.CatalogProduct;
import com.grocery.inventory.config.InventoryProperties;
import com.grocery.inventory.dto.request.AddStockRequest;
import com.grocery.inventory.dto.request.SetStockRequest;
import com.grocery.inventory.dto.response.StockResponse;
import com.grocery.inventory.entity.Stock;
import com.grocery.inventory.exception.BusinessException;
import com.grocery.inventory.exception.ResourceNotFoundException;
import com.grocery.inventory.mapper.StockEventMapper;
import com.grocery.inventory.mapper.StockMapper;
import com.grocery.inventory.messaging.OutboxEventPublisher;
import com.grocery.inventory.messaging.StockEventPublisher;
import com.grocery.inventory.repository.StockRepository;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorInventoryServiceImplTest {

    private static final Long STORE_ID = 1L;
    private static final Long OTHER_STORE_ID = 2L;

    @Mock
    private StockRepository stockRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private ProductCatalogClient productCatalogClient;

    private VendorInventoryServiceImpl vendorInventoryService;

    @BeforeEach
    void setUp() {
        StockEventPublisher stockEventPublisher = new StockEventPublisher(stockRepository, new StockEventMapper(), outboxEventPublisher);
        vendorInventoryService = new VendorInventoryServiceImpl(stockRepository, new StockMapper(), stockEventPublisher,
                productCatalogClient, new InventoryProperties(5),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));   // runs the callback, no real database
    }

    @Test
    void setStock_createsTheStockRecord_forAProductOfTheVendorsStore() {
        when(stockRepository.findByProductIdAndStoreId(1L, STORE_ID)).thenReturn(Optional.empty());
        when(productCatalogClient.findProduct(1L)).thenReturn(Optional.of(new CatalogProduct(1L, STORE_ID, "Tomato", "ACTIVE")));
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> savedWithVersion(invocation.getArgument(0)));

        StockResponse response = vendorInventoryService.setStock(STORE_ID, 1L, new SetStockRequest(50, null));

        assertThat(response.quantityOnHand()).isEqualTo(50);
        assertThat(response.lowStockThreshold()).isEqualTo(5);   // the default from InventoryProperties
        ArgumentCaptor<StockChanged> event = ArgumentCaptor.forClass(StockChanged.class);
        verify(outboxEventPublisher).publish(eq("inventory"), eq(1L), event.capture());
        assertThat(event.getValue().getAvailableQuantity()).isEqualTo(50);
        assertThat(event.getValue().getStockVersion()).isZero();
    }

    @Test
    void setStock_saysNotFound_forAProductOfAnotherStore() {
        when(stockRepository.findByProductIdAndStoreId(11L, STORE_ID)).thenReturn(Optional.empty());
        when(productCatalogClient.findProduct(11L))
                .thenReturn(Optional.of(new CatalogProduct(11L, OTHER_STORE_ID, "Organic Spinach", "ACTIVE")));

        assertThatThrownBy(() -> vendorInventoryService.setStock(STORE_ID, 11L, new SetStockRequest(50, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(stockRepository, never()).save(any());
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void setStock_rejectsADeletedProduct() {
        when(stockRepository.findByProductIdAndStoreId(9L, STORE_ID)).thenReturn(Optional.empty());
        when(productCatalogClient.findProduct(9L)).thenReturn(Optional.of(new CatalogProduct(9L, STORE_ID, "Chips", "DELETED")));

        assertThatThrownBy(() -> vendorInventoryService.setStock(STORE_ID, 9L, new SetStockRequest(50, null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void setStock_updatesAnExistingRecord_withoutCallingTheCatalog() {
        Stock stock = existingStock(1L, 120, 5);
        when(stockRepository.findByProductIdAndStoreId(1L, STORE_ID)).thenReturn(Optional.of(stock));

        StockResponse response = vendorInventoryService.setStock(STORE_ID, 1L, new SetStockRequest(80, 10));

        assertThat(response.quantityOnHand()).isEqualTo(80);
        assertThat(response.lowStockThreshold()).isEqualTo(10);
        verify(stockRepository).flush();
        verifyNoInteractions(productCatalogClient);
    }

    @Test
    void setStock_publishesLowStockDetected_whenTheStockBecomesLow() {
        when(stockRepository.findByProductIdAndStoreId(1L, STORE_ID)).thenReturn(Optional.of(existingStock(1L, 120, 5)));

        vendorInventoryService.setStock(STORE_ID, 1L, new SetStockRequest(4, null));

        List<SpecificRecordBase> events = publishedEvents(2);
        assertThat(events).hasExactlyElementsOfTypes(StockChanged.class, LowStockDetected.class);
        assertThat(((LowStockDetected) events.get(1)).getAvailableQuantity()).isEqualTo(4);
    }

    @Test
    void setStock_doesNotRepeatLowStockDetected_whileTheStockStaysLow() {
        when(stockRepository.findByProductIdAndStoreId(7L, STORE_ID)).thenReturn(Optional.of(existingStock(7L, 3, 5)));

        vendorInventoryService.setStock(STORE_ID, 7L, new SetStockRequest(2, null));

        assertThat(publishedEvents(1)).hasExactlyElementsOfTypes(StockChanged.class);
    }

    @Test
    void addStock_addsToTheExistingStock() {
        when(stockRepository.findByProductIdAndStoreId(7L, STORE_ID)).thenReturn(Optional.of(existingStock(7L, 3, 5)));

        StockResponse response = vendorInventoryService.addStock(STORE_ID, 7L, new AddStockRequest(20));

        assertThat(response.quantityOnHand()).isEqualTo(23);
        assertThat(response.lowStock()).isFalse();
        assertThat(publishedEvents(1)).hasExactlyElementsOfTypes(StockChanged.class);
    }

    @Test
    void addStock_failsWithNotFound_whenTheProductHasNoStockRecordInTheStore() {
        when(stockRepository.findByProductIdAndStoreId(11L, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorInventoryService.addStock(STORE_ID, 11L, new AddStockRequest(20)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(outboxEventPublisher);
    }

    private List<SpecificRecordBase> publishedEvents(int expectedCount) {
        ArgumentCaptor<SpecificRecordBase> events = ArgumentCaptor.forClass(SpecificRecordBase.class);
        verify(outboxEventPublisher, times(expectedCount)).publish(eq("inventory"), any(), events.capture());
        return events.getAllValues();
    }

    private Stock existingStock(Long productId, int quantityOnHand, int threshold) {
        Stock stock = Stock.create(productId, STORE_ID, quantityOnHand, threshold);
        stock.setVersion(3L);
        return stock;
    }

    /** What the database does on insert: the new row gets version 0. */
    private Stock savedWithVersion(Stock stock) {
        stock.setVersion(0L);
        return stock;
    }
}
