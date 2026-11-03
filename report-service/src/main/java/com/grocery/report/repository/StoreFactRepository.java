package com.grocery.report.repository;

import com.grocery.report.entity.StoreFact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreFactRepository extends JpaRepository<StoreFact, Long> {

    long countByStatus(String status);
}
