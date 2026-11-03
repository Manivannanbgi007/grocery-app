package com.grocery.inventory.service.impl;

import com.grocery.inventory.constant.KafkaTopics;
import com.grocery.inventory.dto.response.StockSnapshotResponse;
import com.grocery.inventory.entity.Stock;
import com.grocery.inventory.mapper.StockEventMapper;
import com.grocery.inventory.messaging.OutboxEventPublisher;
import com.grocery.inventory.repository.StockRepository;
import com.grocery.inventory.service.StockSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSnapshotServiceImpl implements StockSnapshotService {

    private final StockRepository stockRepository;
    private final StockEventMapper stockEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * Loads all stock rows at once: fine for this project's data. With millions of rows
     * this would be done in pages, each page in its own transaction.
     *
     * Nothing changes, so every event repeats the current stockVersion. Consumers accept an
     * event with the same version they already have (it simply writes the same values again).
     */
    @Override
    @Transactional
    public StockSnapshotResponse publishSnapshot() {
        List<Stock> allStock = stockRepository.findAll(Sort.by("productId"));
        for (Stock stock : allStock) {
            outboxEventPublisher.publish(KafkaTopics.INVENTORY_AGGREGATE, stock.getProductId(), stockEventMapper.toStockChanged(stock));
        }
        log.info("Stock snapshot: published StockChanged for {} product(s)", allStock.size());
        return new StockSnapshotResponse(allStock.size());
    }
}
