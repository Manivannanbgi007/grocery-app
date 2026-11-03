package com.grocery.report.repository;

import com.grocery.report.entity.StockFact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockFactRepository extends JpaRepository<StockFact, Long> {

    long countByAvailableQuantity(int availableQuantity);
}
