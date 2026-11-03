package com.grocery.inventory.service.impl;

import com.grocery.inventory.dto.common.PageResponse;
import com.grocery.inventory.dto.response.StockResponse;
import com.grocery.inventory.exception.ResourceNotFoundException;
import com.grocery.inventory.mapper.StockMapper;
import com.grocery.inventory.repository.StockRepository;
import com.grocery.inventory.service.InventoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryServiceImpl implements InventoryQueryService {

    private final StockRepository stockRepository;
    private final StockMapper stockMapper;

    @Override
    public PageResponse<StockResponse> searchStock(Long storeId, boolean lowStockOnly, Pageable pageable) {
        return PageResponse.from(stockRepository.search(storeId, lowStockOnly, pageable).map(stockMapper::toResponse));
    }

    @Override
    public StockResponse getStock(Long productId) {
        return stockRepository.findByProductId(productId)
                .map(stockMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No stock record for product " + productId));
    }
}
