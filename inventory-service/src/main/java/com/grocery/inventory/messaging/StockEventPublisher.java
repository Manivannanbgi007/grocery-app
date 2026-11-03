package com.grocery.inventory.messaging;

import com.grocery.inventory.constant.KafkaTopics;
import com.grocery.inventory.entity.Stock;
import com.grocery.inventory.mapper.StockEventMapper;
import com.grocery.inventory.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes what happened to a product's stock. Used for every stock change: by the vendor
 * (set stock, goods received) and by orders (reserve, release, commit).
 */
@Component
@RequiredArgsConstructor
public class StockEventPublisher {

    private final StockRepository stockRepository;
    private final StockEventMapper stockEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * StockChanged always; LowStockDetected only when the stock has just BECOME low
     * (not on every change while it stays low).
     *
     * @param wasLowStock was the stock low before this change?
     */
    @Transactional(propagation = Propagation.MANDATORY)   // part of the transaction that changed the stock
    public void publishChange(Stock stock, boolean wasLowStock) {
        stockRepository.flush();   // run the UPDATE now, so stock.getVersion() is the new version for the event

        outboxEventPublisher.publish(KafkaTopics.INVENTORY_AGGREGATE, stock.getProductId(), stockEventMapper.toStockChanged(stock));
        if (!wasLowStock && stock.isLowStock()) {
            outboxEventPublisher.publish(KafkaTopics.INVENTORY_AGGREGATE, stock.getProductId(), stockEventMapper.toLowStockDetected(stock));
        }
    }
}
