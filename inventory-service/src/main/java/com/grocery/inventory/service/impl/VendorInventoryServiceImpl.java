package com.grocery.inventory.service.impl;

import com.grocery.inventory.client.ProductCatalogClient;
import com.grocery.inventory.client.dto.CatalogProduct;
import com.grocery.inventory.config.InventoryProperties;
import com.grocery.inventory.dto.common.PageResponse;
import com.grocery.inventory.dto.request.AddStockRequest;
import com.grocery.inventory.dto.request.SetStockRequest;
import com.grocery.inventory.dto.response.StockResponse;
import com.grocery.inventory.entity.Stock;
import com.grocery.inventory.exception.BusinessException;
import com.grocery.inventory.exception.ResourceNotFoundException;
import com.grocery.inventory.mapper.StockMapper;
import com.grocery.inventory.messaging.StockEventPublisher;
import com.grocery.inventory.repository.StockRepository;
import com.grocery.inventory.service.VendorInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorInventoryServiceImpl implements VendorInventoryService {

    private final StockRepository stockRepository;
    private final StockMapper stockMapper;
    private final StockEventPublisher stockEventPublisher;
    private final ProductCatalogClient productCatalogClient;
    private final InventoryProperties inventoryProperties;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockResponse> listMyStock(Long storeId, boolean lowStockOnly, Pageable pageable) {
        return PageResponse.from(stockRepository.search(storeId, lowStockOnly, pageable).map(stockMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public StockResponse getMyStock(Long storeId, Long productId) {
        return stockMapper.toResponse(findMyStock(storeId, productId));
    }

    /**
     * Not @Transactional on purpose: the first stock of a product needs a check with product-read-service, made BEFORE
     * the (short) transaction, so a slow service never holds a database connection.
     */
    @Override
    public StockResponse setStock(Long storeId, Long productId, SetStockRequest request) {
        if (stockRepository.findByProductIdAndStoreId(productId, storeId).isEmpty()) {
            // First stock for this product: make sure it really is a product of the vendor's store
            checkProductBelongsToStore(productId, storeId);
        }

        return transactionTemplate.execute(status -> {
            Optional<Stock> existingStock = stockRepository.findByProductIdAndStoreId(productId, storeId);
            if (existingStock.isPresent()) {
                Stock stock = existingStock.get();
                boolean wasLowStock = stock.isLowStock();
                stock.setQuantityOnHand(request.quantityOnHand());
                if (request.lowStockThreshold() != null) {
                    stock.setLowStockThreshold(request.lowStockThreshold());
                }
                log.info("Store id={} set stock of product id={} to {}", storeId, productId, request.quantityOnHand());
                return publishChange(stock, wasLowStock);
            }

            int threshold = request.lowStockThreshold() != null
                    ? request.lowStockThreshold()
                    : inventoryProperties.defaultLowStockThreshold();
            Stock newStock = stockRepository.save(Stock.create(productId, storeId, request.quantityOnHand(), threshold));
            log.info("Store id={} created stock for product id={}: {} unit(s)", storeId, productId, request.quantityOnHand());
            return publishChange(newStock, false);
        });
    }

    @Override
    @Transactional
    public StockResponse addStock(Long storeId, Long productId, AddStockRequest request) {
        Stock stock = findMyStock(storeId, productId);
        boolean wasLowStock = stock.isLowStock();
        stock.addStock(request.quantity());
        log.info("Store id={} added {} unit(s) of product id={}", storeId, request.quantity(), productId);
        return publishChange(stock, wasLowStock);
    }

    /** Publishes StockChanged (and LowStockDetected when the stock just became low), then answers. */
    private StockResponse publishChange(Stock stock, boolean wasLowStock) {
        stockEventPublisher.publishChange(stock, wasLowStock);
        return stockMapper.toResponse(stock);
    }

    /**
     * Asks product-read-service (synchronous REST call). Products of other stores are reported as
     * "not found", so a vendor cannot even learn which product ids other stores use.
     */
    private void checkProductBelongsToStore(Long productId, Long storeId) {
        CatalogProduct product = productCatalogClient.findProduct(productId)
                .filter(catalogProduct -> storeId.equals(catalogProduct.storeId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product %d not found in your store. If you have just created it, try again in a few seconds."
                                .formatted(productId)));
        if (product.isDeleted()) {
            throw new BusinessException("Product %d is deleted, so it cannot get stock".formatted(productId));
        }
    }

    private Stock findMyStock(Long storeId, Long productId) {
        return stockRepository.findByProductIdAndStoreId(productId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product %d has no stock record in your store yet. Set its stock first with PUT /api/v1/vendor/inventory/%d"
                                .formatted(productId, productId)));
    }
}
